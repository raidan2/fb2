package ru.raidan.books.ui;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintWriter;
import java.io.StringWriter;

@Slf4j
public class ErrorMessages {

    public static void warning(String warningMessage) {
        log.warn("{}", warningMessage);
        Platform.runLater(() -> showWarningMessage(warningMessage));
    }

    public static void uncaughtException(Thread t, Throwable e) {
        uncaughtException(e);
    }

    public static void uncaughtException(Throwable e) {
        log.error("Unexpected error", e);
        Platform.runLater(() -> showUncaughtException(e));
    }

    private static void showWarningMessage(String warningMessage) {
        var alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Application Warning");
        alert.setHeaderText("Unexpected application state");
        alert.setContentText(warningMessage);
        alert.show();
    }

    private static void showUncaughtException(Throwable e) {
        var alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Application Error");
        alert.setHeaderText("Unexpected exception thrown");
        alert.setContentText(e.getMessage());

        var writer = new StringWriter();
        try (var printer = new PrintWriter(writer)) {
            e.printStackTrace(printer);
        }

        var textArea = new TextArea(writer.toString());
        textArea.setEditable(false);
        textArea.setWrapText(true);

        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);

        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);

        var pane = new GridPane();
        pane.setMaxWidth(Double.MAX_VALUE);
        pane.add(new Label("Stacktrace:"), 0, 0);
        pane.add(textArea, 0, 1);

        alert.getDialogPane().setExpandableContent(pane);
        alert.getDialogPane().setPrefWidth(800);

        alert.show();
    }

}
