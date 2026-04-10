package ru.raidan.books.ui.about;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import ru.raidan.books.ui.Versions;

public class AboutController {
    @FXML
    Label labelAppName;

    @FXML
    Label labelAppVersion;

    @FXML
    Button buttonClose;

    public void initialize() {
        buttonClose.setDefaultButton(true);
        buttonClose.setCancelButton(true);

        labelAppName.setText(Versions.APP_TITLE);
        labelAppVersion.setText(Versions.APP_VERSION);

        buttonClose.setOnAction(event -> buttonClose.getScene().getWindow().hide());
    }

}
