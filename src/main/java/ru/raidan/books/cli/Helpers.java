package ru.raidan.books.cli;

import lombok.extern.slf4j.Slf4j;
import ru.raidan.books.db.BooksFilter;
import ru.raidan.books.db.CacheDatabase;
import ru.raidan.books.db.LibraryDatabase;
import ru.raidan.books.fb2.Fb2ArchivesParser;
import ru.raidan.books.fb2.Fb2Parser;
import ru.raidan.books.fb2.model.FictionBook;
import ru.raidan.books.operations.AnnotationsService;
import ru.raidan.books.operations.ExportService;
import ru.raidan.books.util.BOMHandler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
public class Helpers {
    private static final Path databasePath = Path.of("storage");

    static void printGenres() throws Exception {
        try (var database = new LibraryDatabase(databasePath)) {
            var queries = database.getQueries();
            queries.printStats();
            queries.printLanguages();
            queries.printGenres();
        }
    }

    static void exportBooksWithGenres(Path exportPath, String genre) throws Exception {
        try (var database = new LibraryDatabase(databasePath)) {
            var queries = database.getQueries();

            var export = new ExportService(queries, new Fb2ArchivesParser());

            var genres = queries.searchGenres(List.of(genre));
            log.info("Genres: {}", genres);

            var filter = BooksFilter.builder()
                    .genres(genres)
                    .lang("ru")
                    .build();

            for (var book : queries.searchBooksWithAuthorsAndGenres(filter)) {
                export.exportBooks(exportPath, book);
            }
        }
    }

    static void lookupAuthorInDB(String match) throws Exception {
        try (var database = new LibraryDatabase(databasePath)) {
            var queries = database.getQueries();

            queries.printStats();

            queries.printGenres();
            queries.printLanguages();

            var authors = queries.searchAuthors("%" + match + "%", 99);
            log.info("Found {} authors", authors.size());
            for (var author : authors) {
                log.info("{}", author);
            }

            if (!authors.isEmpty()) {
                for (var book : queries.searchBooksWithAuthorsAndGenres(
                        BooksFilter.builder().authorId(authors.getFirst().getAuthorId()).lang("ru").limit(10).build()
                )) {
                    log.info("{}", book);
                }

                var genres = queries.searchGenres(List.of("sf_writing", "antique"));
                log.info("Genres: {}", genres);
                for (var book : queries.searchBooksWithAuthorsAndGenres(
                        BooksFilter.builder().genres(genres).lang("ru").limit(100).build()
                )) {
                    log.info("{}", book);
                }

                for (var book : queries.searchBooksWithAuthorsAndGenres(
                        BooksFilter.builder().title("атомный сон").limit(100).build()
                )) {
                    log.info("{}", book);
                }

                try (var cache = new CacheDatabase(databasePath)) {
                    var collector = new AnnotationsService(
                            cache.getQueries(),
                            database.getQueries(),
                            new Fb2ArchivesParser()
                    );
                    log.info("{}", collector.loadBook(34156).bookCache().getAnnotation());
                    log.info("{}", collector.loadBook(258220).bookCache().getAnnotation());

                    var book = collector.loadBook(110119);
                    log.info("{}", book.bookCache().getAnnotation());
                }
            }
        }
    }

    static void lookupAuthors(String... names) throws Exception {
        try (var database = new LibraryDatabase(databasePath)) {
            var queries = database.getQueries();
            queries.printStats();

            for (var name : names) {
                var authors = queries.searchAuthors("%" + name + "%", 99);
                if (authors.isEmpty()) {
                    log.warn("Author not found: {}", name);
                    continue;
                }

                log.info("Top author: {}", authors.getFirst());
            }
        }
    }

    static void exportBooks(Path targetDir, int... authors) throws Exception {
        try (var database = new LibraryDatabase(databasePath)) {
            var export = new ExportService(database.getQueries(), new Fb2ArchivesParser());
            export.exportBooks(targetDir, IntStream.of(authors).boxed().toList());
        }
    }


    static void parseSingleFile(Path path) throws Exception {
        System.out.println("Loading " + path);
        FictionBook fictionBook;
        long time = System.currentTimeMillis();
        try (var input = Files.newInputStream(path)) {
            fictionBook = Fb2Parser.parse(BOMHandler.xmlFromInputStream(input));
        }
        System.out.println("Complete in " + (System.currentTimeMillis() - time) + " msec");
        System.out.println(fictionBook);

        for (var cover : fictionBook.getDescription().getTitleInfo().getCovers().values()) {
            var binary = cover.getBinary();
            if (binary != null) {
                var bytes = binary.toBytes();
                if (bytes != null) {
                    System.out.println("Writing " + binary.getId());
                    Files.write(Path.of(binary.getId()), bytes);
                }
            }
        }
    }

}
