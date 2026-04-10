package ru.raidan.books.db;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;

@Slf4j
public class LibraryDatabase extends AbstractDatabase {

    private static final String SCHEMA_NAME = "LIBRARY_SCHEMA";
    private static final String DATABASE_FILE = "books.db";

    private final LibraryQueries libraryQueries;

    public LibraryDatabase(Path rootDir) throws SQLException, IOException {
        super(getDatabaseConfiguration(rootDir));
        this.libraryQueries = new LibraryQueries(this.dsl);
    }

    public LibraryQueries getQueries() {
        return libraryQueries;
    }

    public static DatabaseConfiguration getDatabaseConfiguration(Path rootDir) {
        return DatabaseConfiguration.builder()
                .rootDir(rootDir)
                .databaseFile(DATABASE_FILE)
                .schemaName(SCHEMA_NAME)
                .initFile("sql/library/books.sql")
                .initFile("sql/library/authors.sql")
                .initFile("sql/library/settings.sql")
                .build();
    }
}
