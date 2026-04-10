package ru.raidan.books.db;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public class AbstractDatabase implements Closeable {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final String databaseFile;
    protected final Connection connection;
    protected final DSLContext dsl;

    public AbstractDatabase(DatabaseConfiguration databaseConfiguration) throws SQLException, IOException {
        this.databaseFile = databaseConfiguration.getDatabaseFile();
        this.connection = databaseConfiguration.getConnection();
        this.dsl = DSL.using(connection);
    }

    @Override
    public void close() {
        log.info("Closing {}", databaseFile);
        try {
            this.connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
