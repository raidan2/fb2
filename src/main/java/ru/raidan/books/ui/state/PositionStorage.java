package ru.raidan.books.ui.state;

import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.prefs.Preferences;

@Slf4j
@RequiredArgsConstructor
public class PositionStorage {

    private static final String USER_APP = "fb2/ru/raidan/books/ui/state";

    private static final String WINDOW_POSITION_X = "Window_Position_X";
    private static final String WINDOW_POSITION_Y = "Window_Position_Y";
    private static final String WINDOW_WIDTH = "Window_Width";
    private static final String WINDOW_HEIGHT = "Window_Height";
    private static final String WINDOW_MAXIMIZED = "Window_Maximized";

    public void registerStage(Stage stage, String stageName) {
        var pathName = USER_APP + "/" + stageName;
        log.info("Load position, pathName={}", pathName);

        // This is pathetic
        var state = new Object() {
            boolean maximized;
            double x;
            double y;
            double width;
            double height;
        };
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, event -> {
            var origX = stage.getX();
            var origY = stage.getY();
            var origWidth = stage.getScene().getWidth();
            var origHeight = stage.getScene().getHeight();
            var origMaximized = false;

            var prefs = Preferences.userRoot().node(pathName);
            var x = prefs.getDouble(WINDOW_POSITION_X, origX);
            var y = prefs.getDouble(WINDOW_POSITION_Y, origY);
            var width = prefs.getDouble(WINDOW_WIDTH, origWidth);
            var height = prefs.getDouble(WINDOW_HEIGHT, origHeight);
            var maximized = prefs.getBoolean(WINDOW_MAXIMIZED, origMaximized);

            log.info("Apply loaded values, x={} -> {}, y={} -> {}, width={} -> {}, height={} -> {}, maximized={} -> {}",
                    origX, x, origY, y, origWidth, width, origHeight, height, origMaximized, maximized);

            // Sometimes it just does not work
            stage.setX(x);
            stage.setY(y);

            if (Double.compare(origWidth, width) != 0 || Double.compare(origHeight, height) != 0) {
                stage.setWidth(width);
                stage.setHeight(height);
            }

            if (origMaximized != maximized) {
                stage.setMaximized(true);
            }

            state.x = x;
            state.y = y;
            state.width = width;
            state.height = height;
            state.maximized = maximized;
        });

        stage.xProperty().addListener((observable, oldValue, newValue) -> {
            if (!state.maximized) {
                state.x = newValue.doubleValue();
            }
        });

        stage.yProperty().addListener((observable, oldValue, newValue) -> {
            if (!state.maximized) {
                state.y = newValue.doubleValue();
            }
        });

        stage.maximizedProperty().addListener((observable, oldValue, newValue) -> state.maximized = newValue);


        var scene = stage.getScene();
        scene.widthProperty().addListener((observable, oldValue, newValue) -> {
            if (!state.maximized) {
                state.width = newValue.doubleValue();
            }
        });

        scene.heightProperty().addListener((observable, oldValue, newValue) -> {
            if (!state.maximized) {
                state.height = newValue.doubleValue();
            }
        });

        stage.addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST, event -> {
            var x = state.x;
            var y = state.y;
            var width = state.width;
            var height = state.height;
            var maximized = state.maximized;

            log.info("Saving position, pathName={}, x={}, y={}, width={}, height={}, maximized={}",
                    pathName, x, y, width, height, maximized);

            var prefs = Preferences.userRoot().node(pathName);
            prefs.putDouble(WINDOW_POSITION_X, x);
            prefs.putDouble(WINDOW_POSITION_Y, y);
            prefs.putDouble(WINDOW_WIDTH, width);
            prefs.putDouble(WINDOW_HEIGHT, height);
            prefs.putBoolean(WINDOW_MAXIMIZED, state.maximized);
        });

    }
}
