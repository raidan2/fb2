package ru.raidan.books.ui.main;

import javafx.application.Platform;

public class MenuController {

    MenuController(Controller controller) {
        controller.menuQuit.setOnAction(event -> Platform.exit());
        controller.menuAbout.setOnAction(event -> controller.getAboutStage().show());
    }
}
