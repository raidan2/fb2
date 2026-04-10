package ru.raidan.books.operations;

import lombok.extern.slf4j.Slf4j;
import ru.raidan.books.db.LibraryImportDatabase;
import ru.raidan.books.db.model.library.tables.pojos.Library;
import ru.raidan.books.inpx.InpxParser;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;

@Slf4j
public class ImportService {

    private ImportService() {
    }

    public static void importLibrary(
            Path databasePath,
            Library library,
            boolean firstFileOnly,
            boolean cleanCache
    ) throws SQLException, IOException {
        log.info("Import library from {} to databasePath={}", library.getInpxLocation(), databasePath);
        var database = new LibraryImportDatabase(databasePath);
        try (var importer = database.startImport(cleanCache)) {
            new InpxParser(importer, firstFileOnly).parseLibrary(library);
        }
        log.info("Import complete");
    }
}
