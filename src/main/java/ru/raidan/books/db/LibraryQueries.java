package ru.raidan.books.db;

import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import ru.raidan.books.db.model.library.tables.pojos.Archive;
import ru.raidan.books.db.model.library.tables.pojos.Author;
import ru.raidan.books.db.model.library.tables.pojos.AuthorToBook;
import ru.raidan.books.db.model.library.tables.pojos.Book;
import ru.raidan.books.db.model.library.tables.pojos.BookToGenre;
import ru.raidan.books.db.model.library.tables.pojos.Genre;
import ru.raidan.books.db.model.library.tables.pojos.Language;
import ru.raidan.books.db.model.library.tables.pojos.Library;
import ru.raidan.books.util.Memoized;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.count;
import static ru.raidan.books.db.model.library.tables.Archive.ARCHIVE;
import static ru.raidan.books.db.model.library.tables.Author.AUTHOR;
import static ru.raidan.books.db.model.library.tables.AuthorToBook.AUTHOR_TO_BOOK;
import static ru.raidan.books.db.model.library.tables.Book.BOOK;
import static ru.raidan.books.db.model.library.tables.BookToGenre.BOOK_TO_GENRE;
import static ru.raidan.books.db.model.library.tables.Genre.GENRE;
import static ru.raidan.books.db.model.library.tables.Language.LANGUAGE;
import static ru.raidan.books.db.model.library.tables.Library.LIBRARY;

@Slf4j
public class LibraryQueries extends AbstractQueries {

    private final Supplier<Library> library = Memoized.memoize(this::loadLibrary);
    private final Supplier<Map<Integer, Archive>> archives = Memoized.memoize(this::loadArchives);
    private final Supplier<Map<String, Genre>> genres = Memoized.memoize(this::loadGenres);
    private final Supplier<Map<String, Language>> languages = Memoized.memoize(this::loadLanguages);
    private final Supplier<Integer> booksCount = Memoized.memoize(this::loadBooksCount);
    private final Supplier<Integer> booksToGenresCount = Memoized.memoize(this::loadBooksToGenresCount);
    private final Supplier<Integer> authorsCount = Memoized.memoize(this::loadAuthorsCount);
    private final Supplier<Integer> authorsToBooksCount = Memoized.memoize(this::loadAuthorsToBooksCount);

    public LibraryQueries(DSLContext dsl) {
        super(dsl);
    }

    public void printStats() {
        execute("Statistics", cfg -> {
            log.info("Library: {}", library.get());
            log.info("Total archive files: {}", archives.get().size());
            log.info("Total books: {}", booksCount.get());
            log.info("Total books genres: {}", genres.get().size());
            log.info("Total books genres mappings: {}", booksToGenresCount.get());
            log.info("Total authors: {}", authorsCount.get());
            log.info("Total books to authors: {}", authorsToBooksCount.get());
            log.info("Total languages: {}", languages.get().size());
        });
    }

    public void printGenres() {
        for (var record : genres.get().values()) {
            log.info("{}", record);
        }
    }

    public void printLanguages() {
        for (var record : languages.get().values()) {
            log.info("{}", record);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public List<Author> searchAuthors(String namePart, int limit) {
        return executeResult("Top " + limit + " authors like " + namePart, cfg -> {
            var select = cfg.dsl()
                    .select(AUTHOR.asterisk())
                    .from(AUTHOR)
                    .join(AUTHOR_TO_BOOK).on(AUTHOR_TO_BOOK.AUTHOR_ID.eq(AUTHOR.AUTHOR_ID));
                    if (!namePart.isEmpty() && !namePart.equals("%%")) {
                        select.where(AUTHOR.ORIGIN.like(namePart.toLowerCase()));
                    }
                    return select.groupBy(AUTHOR.AUTHOR_ID, AUTHOR.SURNAME, AUTHOR.NAME, AUTHOR.MIDDLE_NAME, AUTHOR.ORIGIN)
                            .orderBy(count().desc(), AUTHOR.SURNAME, AUTHOR.NAME, AUTHOR.MIDDLE_NAME)
                            .limit(limit)
                            .fetchStreamInto(Author.class)
                            .toList();
                }
        );
    }

    public List<Integer> searchGenres(List<String> genres) {
        var genresMap = this.genres.get();
        return genres.stream()
                .map(genresMap::get)
                .filter(Objects::nonNull)
                .map(Genre::getGenreId)
                .toList();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public List<Book> searchBooks(BooksFilter booksFilter) {
        return executeResult("Book search", cfg -> {
            var select = cfg.dsl().select(BOOK.asterisk()).from(BOOK);

            if (booksFilter.getAuthorId() != null) {
                select.join(AUTHOR_TO_BOOK).on(AUTHOR_TO_BOOK.BOOK_ID.eq(BOOK.BOOK_ID));
                select.where(AUTHOR_TO_BOOK.AUTHOR_ID.eq(booksFilter.getAuthorId()));
            }

            if (booksFilter.getLang() != null) {
                select.where(BOOK.LANG.eq(booksFilter.getLang()));
            }

            if (booksFilter.getTitle() != null) {
                select.where(BOOK.TITLE.eq(booksFilter.getTitle()));
            }

            if (booksFilter.getGenres() != null && !booksFilter.getGenres().isEmpty()) {
                select.join(BOOK_TO_GENRE).on(BOOK_TO_GENRE.BOOK_ID.eq(BOOK.BOOK_ID));
                select.where(BOOK_TO_GENRE.GENRE_ID.in(booksFilter.getGenres()));
            }

            if (booksFilter.getBookIds() != null && !booksFilter.getBookIds().isEmpty()) {
                select.where(BOOK.BOOK_ID.in(booksFilter.getBookIds()));
            }

            return select
                    .orderBy(BOOK.SERIES, BOOK.SERIES_NUMBER, BOOK.TITLE, BOOK.BOOK_ID)
                    .limit(booksFilter.getLimit())
                    .fetchStreamInto(Book.class)
                    .toList();
        });
    }

    public List<BookWithAuthorsAndGenres> searchBooksWithAuthorsAndGenres(BooksFilter booksFilter) {
        var books = searchBooks(booksFilter);

        var bookIds = books.stream().map(Book::getBookId).toList();

        var genres = executeResult(bookIds.size() + " books to genres", cfg ->
                cfg.dsl()
                        .select(BOOK_TO_GENRE.asterisk())
                        .from(BOOK_TO_GENRE)
                        .where(BOOK_TO_GENRE.BOOK_ID.in(bookIds))
                        .fetchStreamInto(BookToGenre.class)
                        .collect(Collectors.groupingBy(BookToGenre::getBookId))
        );

        var authors = executeResult(bookIds.size() + " authors to books", cfg ->
                cfg.dsl()
                        .select(AUTHOR_TO_BOOK.asterisk())
                        .from(AUTHOR_TO_BOOK)
                        .where(AUTHOR_TO_BOOK.BOOK_ID.in(bookIds))
                        .fetchStreamInto(AuthorToBook.class)
                        .collect(Collectors.groupingBy(AuthorToBook::getBookId))
        );

        return books.stream()
                .map(book -> new BookWithAuthorsAndGenres(
                        book,
                        authors.get(book.getBookId()),
                        genres.get(book.getBookId())
                ))
                .toList();

    }

    public Library getLibrary() {
        return library.get();
    }

    public Map<Integer, Archive> getArchives() {
        return Collections.unmodifiableMap(archives.get());
    }

    public Map<String, Language> getLanguages() {
        return Collections.unmodifiableMap(languages.get());
    }

    public List<Author> loadAuthors(List<Integer> authorIds) {
        return executeResult(authorIds.size() + " authors", cfg ->
                cfg.dsl()
                        .select(AUTHOR.asterisk())
                        .from(AUTHOR)
                        .where(AUTHOR.AUTHOR_ID.in(authorIds))
                        .fetchInto(Author.class));
    }

    public Book loadBook(int bookId) {
        return executeResult("Book " + bookId, cfg ->
                cfg.dsl()
                        .select(BOOK.asterisk())
                        .from(BOOK)
                        .where(BOOK.BOOK_ID.eq(bookId))
                        .fetchSingleInto(Book.class));
    }

    public Archive getArchive(int archiveId) {
        var record = archives.get().get(archiveId);
        if (record == null) {
            throw new IllegalArgumentException("Unable to find archive " + archiveId);
        }
        return record;
    }

    private Library loadLibrary() {
        return executeResult("Library", cfg ->
                cfg.dsl()
                        .select(LIBRARY.asterisk())
                        .from(LIBRARY)
                        .fetchSingleInto(Library.class)
        );
    }

    private Map<Integer, Archive> loadArchives() {
        return executeResult("loadArchives", cfg ->
                cfg.dsl()
                        .select(ARCHIVE.asterisk())
                        .from(ARCHIVE)
                        .fetchStreamInto(Archive.class)
                        .collect(Collectors.toMap(Archive::getArchiveId, Function.identity()))
        );
    }

    private Map<String, Genre> loadGenres() {
        return executeResult("loadGenres", cfg ->
                cfg.dsl()
                        .select(GENRE.asterisk())
                        .from(GENRE)
                        .orderBy(GENRE.TOTAL_BOOKS.desc(), GENRE.GENRE_ID)
                        .fetchStreamInto(Genre.class)
                        .collect(Collectors.toMap(Genre::getGenre, Function.identity(), (x, y) -> y, LinkedHashMap::new))
        );
    }

    private Map<String, Language> loadLanguages() {
        return executeResult("loadLanguages", cfg ->
                cfg.dsl()
                        .select(LANGUAGE.asterisk())
                        .from(LANGUAGE)
                        .orderBy(LANGUAGE.TOTAL_BOOKS.desc(), LANGUAGE.LANG)
                        .fetchStreamInto(Language.class)
                        .collect(Collectors.toMap(Language::getLang, Function.identity(), (x, y) -> y, LinkedHashMap::new))
        );
    }

    private int loadBooksCount() {
        return executeResult("loadBooksCount", cfg -> cfg.dsl().fetchCount(BOOK));
    }

    private int loadBooksToGenresCount() {
        return executeResult("loadBooksToGenresCount", cfg -> cfg.dsl().fetchCount(BOOK_TO_GENRE));
    }

    private int loadAuthorsCount() {
        return executeResult("loadAuthorsCount", cfg -> cfg.dsl().fetchCount(AUTHOR));
    }

    private int loadAuthorsToBooksCount() {
        return executeResult("loadAuthorsToBooksCount", cfg -> cfg.dsl().fetchCount(AUTHOR_TO_BOOK));
    }
}
