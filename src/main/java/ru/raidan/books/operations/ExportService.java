package ru.raidan.books.operations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.raidan.books.db.BookWithAuthorsAndGenres;
import ru.raidan.books.db.BooksFilter;
import ru.raidan.books.db.LibraryQueries;
import ru.raidan.books.db.model.library.tables.pojos.Author;
import ru.raidan.books.db.model.library.tables.pojos.Book;
import ru.raidan.books.fb2.Fb2ArchivesParser;
import ru.raidan.books.util.PojoUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class ExportService {
    private final LibraryQueries libraryQueries;
    private final Fb2ArchivesParser archivesParser;

    public void exportBooks(Path outputDir, List<Integer> authorIds) throws IOException {
        for (var author : libraryQueries.loadAuthors(authorIds)) {
            exportBooks(outputDir, author);
        }
    }

    public void exportBooks(Path outputDir, BookWithAuthorsAndGenres book) throws IOException {
        var authors = libraryQueries.loadAuthors(List.of(book.authors().getFirst().getAuthorId()));
        exportBooks(outputDir, authors.getFirst(), List.of(book.book()), ignore -> {
        });
    }

    public void exportBooks(Path outputDir, Author author) throws IOException {
        var filter = BooksFilter.builder()
                .authorId(author.getAuthorId())
                .lang("ru")
                .build();
        var books = libraryQueries.searchBooks(filter);

        exportBooks(outputDir, author, books, ignore -> {
        });
    }

    public Map<Integer, Path> exportBooks(
            Path outputDir,
            Author author,
            List<Book> books,
            IntConsumer progressListener
    ) throws IOException {
        long time = System.currentTimeMillis();
        var targetDir = outputDir.resolve(PojoUtils.toName(author));

        log.info("Export books for authorId={} into targetDir={}", author.getAuthorId(), targetDir);
        Files.createDirectories(targetDir);

        var libraryLocation = Path.of(libraryQueries.getLibrary().getLibraryLocation());

        log.info("Total books loaded: {}", books.size());

        var byArchive = books.stream().collect(Collectors.groupingBy(Book::getArchiveId));
        log.info("Total archives to use: {}", byArchive.size());

        var result = new HashMap<Integer, Path>();
        for (var e : byArchive.entrySet()) {
            var archive = libraryQueries.getArchive(e.getKey());

            var exports = new HashMap<String, Path>();
            for (var book : e.getValue()) {
                var targetFile = targetDir;
                if (!book.getSeries().isEmpty()) {
                    targetFile = targetFile.resolve(book.getSeries());
                }

                targetFile = targetFile.resolve(PojoUtils.toFullNameOnFilesystem(book));

                result.put(book.getBookId(), targetFile);

                if (Files.exists(targetFile)) {
                    log.info("Book already exists, targetFile={}, book={}", targetFile, book);
                    continue;
                }

                exports.put(book.getFileName(), targetFile);
            }

            if (!exports.isEmpty()) {
                log.info("Exporting {} files from archiveName={}", exports.size(), archive.getArchiveName());
                archivesParser.exportFictionBook(
                        libraryLocation,
                        archive.getArchiveName(),
                        exports
                );
            }

            progressListener.accept(result.size());
        }

        log.info("Export complete, total books: {}, took {} msec",
                result.size(), System.currentTimeMillis() - time);
        return result;
    }

}
