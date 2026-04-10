package ru.raidan.books.db;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

@Slf4j
@Value
@Builder
public class DatabaseConfiguration {

    Path rootDir;
    String databaseFile;
    String schemaName;
    @Singular
    List<String> initFiles;

    public Connection getConnection() throws SQLException, IOException {
        Files.createDirectories(rootDir);

        var url = String.format("jdbc:sqlite:%s/%s", rootDir, getDatabaseFile());
        log.info("Using database: {}", url);

        var connection = DriverManager.getConnection(url, "sa", "");
        this.initTables(connection);

        log.info("Database initialization complete: {}", url);
        return connection;
    }

    public void dropDatabase() throws IOException {
        var path = rootDir.resolve(databaseFile);
        if (Files.exists(path)) {
            Files.delete(path);
        }
    }

    private void initTables(Connection connection) throws SQLException, IOException {
        for (var resourceName : initFiles) {
            initTable(connection, resourceName);
        }
    }

    private void initTable(Connection connection, String resourceName) throws SQLException, IOException {
        log.info("Initializing tables from {}", resourceName);

        var sql = Utils.readResource(resourceName);
        try (var statement = connection.createStatement()) {
            for (var script : sql.split(";")) {
                script = script.trim();
                if (!script.isEmpty()) {
                    statement.execute(script);
                }
            }
        }
    }

}
