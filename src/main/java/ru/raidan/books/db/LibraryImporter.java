package ru.raidan.books.db;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import ru.raidan.books.db.model.library.tables.pojos.AuthorToBook;
import ru.raidan.books.db.model.library.tables.pojos.Library;
import ru.raidan.books.db.model.library.tables.records.ArchiveRecord;
import ru.raidan.books.db.model.library.tables.records.AuthorRecord;
import ru.raidan.books.db.model.library.tables.records.AuthorToBookRecord;
import ru.raidan.books.db.model.library.tables.records.BookRecord;
import ru.raidan.books.db.model.library.tables.records.BookToGenreRecord;
import ru.raidan.books.db.model.library.tables.records.GenreRecord;
import ru.raidan.books.db.model.library.tables.records.LanguageRecord;
import ru.raidan.books.db.model.library.tables.records.LibraryRecord;
import ru.raidan.books.inpx.InpxListener;
import ru.raidan.books.inpx.InpxRawBook;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jooq.impl.DSL.count;
import static ru.raidan.books.db.model.library.tables.Author.AUTHOR;
import static ru.raidan.books.db.model.library.tables.AuthorToBook.AUTHOR_TO_BOOK;
import static ru.raidan.books.db.model.library.tables.BookToGenre.BOOK_TO_GENRE;
import static ru.raidan.books.db.model.library.tables.Genre.GENRE;
import static ru.raidan.books.db.model.library.tables.Library.LIBRARY;

@Slf4j
@RequiredArgsConstructor
public class LibraryImporter implements InpxListener, AutoCloseable {

    private static final int BATCH_LIMIT = 10_000;

    private final AtomicInteger authorIdGen = new AtomicInteger();
    private final AtomicInteger genreIdGen = new AtomicInteger();
    private final AtomicInteger archiveIdGen = new AtomicInteger();
    private final Map<String, Integer> authorIdMapping = new HashMap<>();
    private final Map<String, Integer> genreIdMapping = new HashMap<>();
    private final Map<String, Integer> archiveIdMapping = new HashMap<>();
    private final Set<Integer> knownBooks = new HashSet<>(100_000);
    private final Set<Integer> knownAuthors = new HashSet<>(100_000);
    private final Set<AuthorToBook> knownAuthorsMapping = new HashSet<>(100_000);

    private final List<GenreRecord> genres = new ArrayList<>();
    private final List<ArchiveRecord> archives = new ArrayList<>();
    private final List<BookRecord> books = new ArrayList<>();
    private final List<BookToGenreRecord> booksGenres = new ArrayList<>();
    private final List<AuthorRecord> authors = new ArrayList<>();
    private final List<AuthorToBookRecord> authorsToBooks = new ArrayList<>();
    private final Map<String, LanguageRecord> languages = new HashMap<>();

    private final Connection connection;
    private final DSLContext dsl;

    @Override
    public void onBegin(Library library) {
        // TODO: read current database state and restore mappings/sets
        dsl.transaction(cfg -> {
            log.info("Registering collection...");
            cfg.dsl().insertInto(LIBRARY)
                    .set(new LibraryRecord(library))
                    .execute();
        });
    }

    @Override
    public void onRecordParsed(InpxRawBook rawBook) {

        BookRecord book;
        List<AuthorRecord> authors;
        try {
            book = parseBook(rawBook);
            authors = parseAuthors(rawBook);
        } catch (Exception e) {
            log.error("Unable to parse book record {}: {}", rawBook, e.getMessage());
            return;
        }

        if (knownBooks.add(book.getBookId())) {
            this.books.add(book);
            this.booksGenres.addAll(parseBookGenres(rawBook));

            var language = languages.computeIfAbsent(book.getLang(), lang -> new LanguageRecord(lang, 0));
            language.setTotalBooks(language.getTotalBooks() + 1);
        }

        for (var author : authors) {
            if (knownAuthors.add(author.getAuthorId())) {
                this.authors.add(author);
            }

            var mappingRecord = createBookToRecord(book, author);
            if (knownAuthorsMapping.add(mappingRecord)) {
                this.authorsToBooks.add(createBookToRecord(mappingRecord));
            }
        }

        var limit = BATCH_LIMIT;
        if (archives.size() >= limit || books.size() >= limit || genres.size() >= limit ||
                booksGenres.size() >= limit || authors.size() >= limit || authorsToBooks.size() >= limit) {
            sync();
        }
    }

    @Override
    public void onFileComplete() {
        sync();
    }

    @Override
    public void onComplete() {
        this.updateGenres();
        this.updateAuthorToBook();
        dsl.transaction(cfg -> cfg.dsl().batchInsert(languages.values()).execute());

        new LibraryQueries(dsl).printStats();
        log.info("Database loading is complete");
    }

    private void sync() {
        log.info("Sync to database, archives={}, books={}, genres={}, booksGenres={}, authors={}, authorsToBooks={}",
                archives.size(), books.size(), genres.size(), booksGenres.size(), authors.size(), authorsToBooks.size());
        dsl.transaction(cfg -> {
            cfg.dsl().batchInsert(archives).execute();
            cfg.dsl().batchInsert(books).execute();
            cfg.dsl().batchInsert(genres).execute();
            cfg.dsl().batchInsert(booksGenres).execute();
            cfg.dsl().batchInsert(authors).execute();
            cfg.dsl().batchInsert(authorsToBooks).execute();
        });

        archives.clear();
        books.clear();
        genres.clear();
        booksGenres.clear();
        authors.clear();
        authorsToBooks.clear();
    }

    private void updateGenres() {
        dsl.transaction(cfg -> {
            log.info("Updating genres...");
            cfg.dsl().update(GENRE)
                    .set(GENRE.TOTAL_BOOKS,
                            cfg.dsl().select(count())
                                    .from(BOOK_TO_GENRE)
                                    .where(BOOK_TO_GENRE.GENRE_ID.eq(GENRE.GENRE_ID))
                    ).execute();
        });
    }

    private void updateAuthorToBook() {
        dsl.transaction(cfg -> {
            log.info("Updating books count...");
            cfg.dsl().update(AUTHOR)
                    .set(AUTHOR.TOTAL_BOOKS,
                            cfg.dsl().select(count())
                                    .from(AUTHOR_TO_BOOK)
                                    .where(AUTHOR_TO_BOOK.AUTHOR_ID.eq(AUTHOR.AUTHOR_ID))
                    ).execute();
        });
    }

    @Override
    public void close() {
        log.info("Compacting and closing database...");
        try {
            try (var statement = connection.createStatement()) {
                statement.execute("vacuum");
            }
            this.connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private AuthorToBook createBookToRecord(BookRecord book, AuthorRecord author) {
        return new AuthorToBook(author.getAuthorId(), book.getBookId());
    }

    private AuthorToBookRecord createBookToRecord(AuthorToBook map) {
        return new AuthorToBookRecord(map);
    }

    private Integer createArchivesRecord(InpxRawBook rawBook) {
        var archive = rawBook.getArchive();

        var archiveId = archiveIdMapping.get(archive);
        if (archiveId == null) {
            archiveId = archiveIdGen.incrementAndGet();
            archiveIdMapping.put(archive, archiveId);

            var archiveRecord = new ArchiveRecord();
            archiveRecord.setArchiveId(archiveId);
            archiveRecord.setArchiveName(archive);
            this.archives.add(archiveRecord);
        }
        return archiveId;
    }

    private BookRecord parseBook(InpxRawBook rawBook) {
        var book = new BookRecord();

        book.setBookId(rawBook.getBookId());
        book.setTitle(rawBook.getTitle());
        book.setSeries(rawBook.getSeries());
        book.setSeriesNumber(rawBook.getSeriesNumber());
        book.setFileName(rawBook.getFileName() + "." + rawBook.getFileExt());
        book.setSize(rawBook.getSize());
        book.setDate(rawBook.getDate());
        book.setLang(rawBook.getLang().toLowerCase());
        book.setFileIndex(rawBook.getIndex());
        book.setKeywords(rawBook.getKeywords());
        book.setArchiveId(createArchivesRecord(rawBook));
        book.setArchiveMatched(rawBook.isArchiveMatched() ? 1 : 0);

        return book;
    }

    private List<BookToGenreRecord> parseBookGenres(InpxRawBook rawBook) {
        return Arrays.stream(rawBook.getGenres().split(":"))
                .map(genre -> genre.toLowerCase().trim())
                .filter(genre -> !genre.isEmpty())
                .map(genre -> {
                    var genreId = genreIdMapping.get(genre);
                    if (genreId == null) {
                        genreId = genreIdGen.incrementAndGet();
                        genreIdMapping.put(genre, genreId);

                        var genreRec = new GenreRecord();
                        genreRec.setGenreId(genreId);
                        genreRec.setGenre(genre);

                        genres.add(genreRec);
                    }

                    var record = dsl.newRecord(BOOK_TO_GENRE);

                    record.setBookId(rawBook.getBookId());
                    record.setGenreId(genreId);

                    return record;
                }).toList();
    }

    private List<AuthorRecord> parseAuthors(InpxRawBook rawBook) {
        return Arrays.stream(rawBook.getAuthor().split(":"))
                .map(rawAuthor -> {
                    var trimmedName = rawAuthor.trim();
                    var originName = trimmedName.toLowerCase();
                    var authorId = authorIdMapping.computeIfAbsent(
                            originName,
                            hash -> authorIdGen.incrementAndGet()
                    );

                    var author = new AuthorRecord();

                    author.setAuthorId(authorId);
                    author.setOrigin(originName);

                    var names = trimmedName.split(",");
                    if (names.length == 0) {
                        author.setSurname("");
                    } else {
                        author.setSurname(names[0].trim());
                        if (names.length > 1) {
                            author.setName(names[1].trim());
                        }
                        if (names.length > 2) {
                            author.setMiddleName(names[2].trim());
                        }
                    }
                    return author;
                }).toList();
    }

}
