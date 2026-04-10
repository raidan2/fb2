package ru.raidan.books.operations;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.raidan.books.db.BooksFilter;
import ru.raidan.books.db.LibraryDatabase;
import ru.raidan.books.db.model.library.tables.pojos.Archive;
import ru.raidan.books.db.model.library.tables.pojos.Language;
import ru.raidan.books.db.model.library.tables.pojos.Library;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
class ImportServiceTest {

    @TempDir
    private Path tempDir;

    @Test
    void testImport() throws SQLException, IOException {
        var targetInpxFile = tempDir.resolve("test.inpx");

        InpxTestData.prepare(targetInpxFile);

        var libraryDesc = new Library(
                targetInpxFile.toString(),
                tempDir.toString(),
                "Test Library",
                null
        );

        var databasePath = tempDir.resolve("storage");
        ImportService.importLibrary(
                databasePath,
                libraryDesc,
                false,
                true
        );

        try (var library = new LibraryDatabase(databasePath)) {
            var queries = library.getQueries();
            queries.printStats();
            queries.printGenres();
            queries.printLanguages();

            assertEquals(libraryDesc, queries.getLibrary());

            assertEquals(Map.of(
                    "ru", new Language("ru", 5),
                    "en", new Language("en", 2)
            ), queries.getLanguages());

            assertEquals(Map.of(
                            1, new Archive(1, "f1.zip"),
                            2, new Archive(2, "f2.zip")
                    ),
                    queries.getArchives());

            var books = queries.searchBooks(BooksFilter.builder().build());
            for (var book : books) {
                log.info("{}", book);
            }
            assertEquals(
                    List.of(
                            InpxTestData.book4(),
                            InpxTestData.book3(),
                            InpxTestData.book5(),
                            InpxTestData.book11(),
                            InpxTestData.book12(),
                            InpxTestData.book1(),
                            InpxTestData.book2()
                    ),
                    books
            );
        }
    }
}