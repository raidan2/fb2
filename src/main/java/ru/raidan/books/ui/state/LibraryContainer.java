package ru.raidan.books.ui.state;

import lombok.Getter;
import ru.raidan.books.db.CacheDatabase;
import ru.raidan.books.db.CacheQueries;
import ru.raidan.books.db.LibraryDatabase;
import ru.raidan.books.db.LibraryQueries;
import ru.raidan.books.fb2.Fb2ArchivesParser;
import ru.raidan.books.operations.AnnotationsService;
import ru.raidan.books.operations.ExportService;

import java.io.Closeable;
import java.nio.file.Path;

@Getter
public class LibraryContainer implements Closeable {
    private final Path databasePath;
    private final LibraryDatabase libraryDatabase;
    private final CacheDatabase cacheDatabase;

    private final AnnotationsService annotationsService;
    private final ExportService exportService;

    public LibraryContainer(Path databasePath) {
        this.databasePath = databasePath;
        try {
            this.libraryDatabase = new LibraryDatabase(databasePath);
            this.cacheDatabase = new CacheDatabase(databasePath);
            var archivesParser = new Fb2ArchivesParser();
            this.annotationsService = new AnnotationsService(getCache(), getQueries(), archivesParser);
            this.exportService = new ExportService(getQueries(), archivesParser);
        } catch (Exception e) {
            throw new RuntimeException("Unable to load database from " + databasePath, e);
        }
    }

    public LibraryQueries getQueries() {
        return libraryDatabase.getQueries();
    }

    public CacheQueries getCache() {
        return cacheDatabase.getQueries();
    }

    @Override
    public void close() {
        this.libraryDatabase.close();
        this.cacheDatabase.close(); // TODO: compact?
    }
}
