package ru.raidan.books.ui.state;

import javafx.concurrent.Service;
import javafx.concurrent.Task;
import lombok.extern.slf4j.Slf4j;
import ru.raidan.books.ui.ErrorMessages;

import java.io.Closeable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
public class AppExecutor implements Closeable {

    private final ExecutorService executor = Executors.newFixedThreadPool(
            1,
            runnable -> {
                var thread = new Thread(runnable);
                thread.setName("App Transaction");
                thread.setDaemon(true);
                return thread;
            }
    );

    @Override
    public void close() {
        executor.shutdown();
    }

    public void runAsync(Runnable action) {
        loadAsync(
                () -> {
                    action.run();
                    return null;
                },
                () -> {
                },
                ignore -> {
                }
        );
    }

    public <T> void loadAsync(
            Supplier<T> function,
            Runnable onStart,
            Consumer<T> onSuccess
    ) {

        var service = new Service<T>() {
            @SuppressWarnings("unchecked")
            @Override
            protected Task<T> createTask() {
                var task = new Task<T>() {
                    @Override
                    protected T call() {
                        try {
                            return function.get();
                        } catch (Throwable t) {
                            ErrorMessages.uncaughtException(t);
                            throw t;
                        }
                    }
                };
                task.setOnScheduled(event -> onStart.run());
                task.setOnSucceeded(event -> onSuccess.accept((T) event.getSource().getValue()));
                return task;
            }
        };
        service.setExecutor(executor);
        service.start();
    }
}
