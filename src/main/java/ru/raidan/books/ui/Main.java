package ru.raidan.books.ui;

import javafx.application.Application;
import javafx.stage.Stage;
import ru.raidan.books.ui.main.Settings;

import java.nio.file.Path;

public class Main extends Application {

    private static final String USER_HOME = System.getProperty("user.home");
    private static final Path LAST_DATABASE_PATH = Path.of(USER_HOME).resolve(".fb2-storage");

    private final Settings settings = Settings.builder()
            .lastDatabasePath(LAST_DATABASE_PATH)
            .exportBooksPath(Path.of(USER_HOME).resolve("books-export"))
            .bookReaderArgs(new String[]{"/snap/bin/fbreader"})
            .build();

    private MainApp app;

    @Override
    public void start(Stage stage) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler(ErrorMessages::uncaughtException);
        app = new MainApp(settings, true);
        app.configureMainStage(stage);
    }

    @Override
    public void stop() {
        if (app != null) {
            app.close();
        }
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            if (args.length > 1 && "--import".equals(args[0])) {
                // ./fb2 --import "/media/miroslav/T7/books/fb2.Flibusta.Net/flibusta_fb2_local.inpx"
                Import.importWithoutUI(Path.of(args[1]), LAST_DATABASE_PATH);
            } else {
                System.err.println("Usage: fb2 --import <inpx file location>");
                System.exit(1);
            }
        }
        launch();
    }
}
