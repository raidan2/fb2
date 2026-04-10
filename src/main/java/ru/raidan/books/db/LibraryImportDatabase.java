package ru.raidan.books.db;

import lombok.extern.slf4j.Slf4j;
import org.jooq.impl.DSL;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;

@Slf4j
public class LibraryImportDatabase {

    private final DatabaseConfiguration configuration;

    public LibraryImportDatabase(Path rootDir) {
        this.configuration = LibraryDatabase.getDatabaseConfiguration(rootDir);
    }

    public LibraryImporter startImport(boolean cleanCache) throws IOException, SQLException {
        log.info("Remove old library database");
        configuration.dropDatabase();

        if (cleanCache) {
            log.info("Remove old cache database");
            CacheDatabase.getDatabaseConfiguration(configuration.getRootDir()).dropDatabase();
        }

        var connection = configuration.getConnection();
        return new LibraryImporter(connection, DSL.using(connection));
    }
}
