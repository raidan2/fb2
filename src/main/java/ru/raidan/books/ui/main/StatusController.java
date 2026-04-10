package ru.raidan.books.ui.main;

import javafx.scene.control.Label;

import java.util.Objects;

public class StatusController implements StatusListener {

    private final Label labelDatabase;
    private final Label labelStatus;

    StatusController(Controller controller) {
        this.labelDatabase = Objects.requireNonNull(controller.labelDatabase);
        this.labelStatus = Objects.requireNonNull(controller.labelStatus);
    }

    @Override
    public void setLibrary(String library) {
        this.labelDatabase.setText("Library: " + library);
    }

    @Override
    public void setStatus(String text) {
        this.labelStatus.setText(text);
    }

    @Override
    public void setReady() {
        this.setStatus("Ready");
    }
}
