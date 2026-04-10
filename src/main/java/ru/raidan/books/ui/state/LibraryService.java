package ru.raidan.books.ui.state;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import ru.raidan.books.ui.main.StatusListener;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.nio.file.Path;
import java.util.Objects;

@Slf4j
public class LibraryService implements Closeable {

    @Getter
    private final AppExecutor appExecutor = new AppExecutor();
    @Nullable
    private LibraryContainer library;

    public void openLibrary(Path databasePath, StatusListener statusListener, LibraryListener libraryListener) {
        var now = System.currentTimeMillis();
        log.info("Open databasePath={}", databasePath);

        // TODO: lock the screen?
        this.closeLibrary(statusListener);

        appExecutor.loadAsync(
                () -> new LibraryContainer(databasePath),
                () -> statusListener.setStatus("Loading database: [" + databasePath + "]"),
                library -> {
                    LibraryService.this.library = library;

                    log.info("Library loaded from {}, took {} millis",
                            databasePath, System.currentTimeMillis() - now);

                    statusListener.setLibrary("[" + databasePath + "]");
                    statusListener.setReady();

                    libraryListener.onLibraryLoaded(library);
                }
        );
    }

    public void closeLibrary(StatusListener statusListener) {
        if (library != null) {
            log.info("Close library: {}", library.getDatabasePath());
            if (statusListener != null) {
                statusListener.setLibrary("");
            }
            appExecutor.runAsync(library::close);
        }
        library = null;
    }

    public LibraryContainer getLibrary() {
        return Objects.requireNonNull(library, "Internal error, no library selected");
    }

    @Override
    public void close() {
        closeLibrary(null);

        log.info("Close library service");
        appExecutor.close();
    }
}
