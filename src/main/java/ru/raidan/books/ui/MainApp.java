package ru.raidan.books.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import ru.raidan.books.ui.about.AboutController;
import ru.raidan.books.ui.main.Controller;
import ru.raidan.books.ui.main.Settings;
import ru.raidan.books.ui.state.LibraryService;
import ru.raidan.books.ui.state.PositionStorage;
import ru.raidan.books.util.ResourcesUtils;

import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;

@RequiredArgsConstructor
public class MainApp implements Closeable {
    private final LibraryService libraryService = new LibraryService();
    private final PositionStorage positionStorage = new PositionStorage();
    private final Settings settings;
    private final boolean trackPositions;

    private Controller controller;
    private Runnable openLibraryTask;

    @Override
    public void close() {
        this.openLibraryTask = null;
        this.controller = null;
        libraryService.close();
    }

    public void configureMainStage(Stage mainStage) throws IOException {
        configureMainStage(mainStage, getAboutStage());
    }

    private Stage getAboutStage() throws IOException {
        var fxmlLoader = new FXMLLoader(ResourcesUtils.getResource("jfx/about.fxml"));

        var controller = new AboutController();
        fxmlLoader.setController(controller);

        var stage = new Stage();
        stage.setTitle("About");
        stage.setScene(new Scene(fxmlLoader.load()));
        stage.setAlwaysOnTop(true);
        stage.setResizable(false);
        stage.setMaximized(false);

        stage.setOnShown(event -> {
            var window = stage.getScene().getWindow();

            var screenBounds = Screen.getPrimary().getVisualBounds();
            window.setX((screenBounds.getWidth() - window.getWidth()) / 2);
            window.setY((screenBounds.getHeight() - window.getHeight()) / 2);
        });


        return stage;
    }

    private void configureMainStage(Stage mainStage, Stage aboutStage) throws IOException {
        var controller = new Controller(aboutStage, libraryService.getAppExecutor(), settings);

        var fxmlLoader = new FXMLLoader(ResourcesUtils.getResource("jfx/main.fxml"));
        fxmlLoader.setController(controller);

        mainStage.setTitle(Versions.APP_TITLE);
        mainStage.setScene(new Scene(fxmlLoader.load()));

        var statusListener = controller.getStatusListener();
        openLibraryTask = () ->
                libraryService.openLibrary(controller.getSettings().getLastDatabasePath(), statusListener, controller);

        mainStage.setOnShowing(event -> openLibraryTask.run());
        mainStage.setOnCloseRequest(event -> libraryService.closeLibrary(statusListener));

        if (trackPositions) {
            positionStorage.registerStage(mainStage, "main");
        }
        mainStage.show();
        this.controller = controller;
    }

    void openLibrary() {
        Objects.requireNonNull(openLibraryTask, "Internal error, openLibraryTask is null");
        openLibraryTask.run();
    }

    Controller getController() {
        return Objects.requireNonNull(controller, "Internal error, controller is null");
    }
}
