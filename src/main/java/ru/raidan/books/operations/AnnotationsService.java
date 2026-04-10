package ru.raidan.books.operations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.raidan.books.db.CacheQueries;
import ru.raidan.books.db.LibraryQueries;
import ru.raidan.books.db.model.library.tables.pojos.Book;
import ru.raidan.books.fb2.Fb2ArchivesParser;
import ru.raidan.books.fb2.Fb2TextConverter;

import java.io.IOException;
import java.nio.file.Path;

@Slf4j
@RequiredArgsConstructor
public class AnnotationsService {
    private final CacheQueries cacheQueries;
    private final LibraryQueries libraryQueries;
    private final Fb2ArchivesParser archivesParser;
    private final Fb2TextConverter fb2TextConverter = new Fb2TextConverter();

    public BooksWithDetails loadBook(int bookId) {
        var book = libraryQueries.loadBook(bookId);
        return loadBook(book);
    }

    public BooksWithDetails loadBook(Book book) {
        long time = System.currentTimeMillis();
        var bookDetails = cacheQueries.getBookDetails(book.getBookId(), id -> {
            var library = libraryQueries.getLibrary();
            var archive = libraryQueries.getArchive(book.getArchiveId());

            try {
                return archivesParser.loadFictionBook(
                        Path.of(library.getLibraryLocation()),
                        archive.getArchiveName(),
                        book.getFileName()
                );
            } catch (IOException e) {
                throw new RuntimeException("Unable to load book " + book.getBookId(), e);
            }
        }, fb2TextConverter);

        log.info("Load bookId={} complete, took {} msec", book.getBookId(), System.currentTimeMillis() - time);

        return new BooksWithDetails(book, bookDetails);
    }
}
