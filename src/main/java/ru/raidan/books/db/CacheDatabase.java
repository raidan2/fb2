package ru.raidan.books.db;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;

@Slf4j
public class CacheDatabase extends AbstractDatabase {

    private static final String SCHEMA_NAME = "CACHE_SCHEMA";
    private static final String DATABASE_FILE = "cache.db";

    private final CacheQueries cacheQueries;

    public CacheDatabase(Path rootDir) throws SQLException, IOException {
        super(getDatabaseConfiguration(rootDir));
        this.cacheQueries = new CacheQueries(this.dsl);
    }

    public CacheQueries getQueries() {
        return cacheQueries;
    }

    public void compact() {
        log.info("Compacting cache database...");
        try {
            try (var statement = connection.createStatement()) {
                statement.execute("vacuum");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static DatabaseConfiguration getDatabaseConfiguration(Path rootDir) {
        return DatabaseConfiguration.builder()
                .rootDir(rootDir)
                .databaseFile(DATABASE_FILE)
                .schemaName(SCHEMA_NAME)
                .initFile("sql/cache/books.sql")
                .initFile("sql/cache/workspace.sql")
                .build();
    }

}
