package ru.raidan.books.ui;

import com.sun.javafx.scene.control.ContextMenuContent;
import com.sun.javafx.scene.control.MenuBarButton;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeTableView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.Stop;
import org.testfx.service.query.EmptyNodeQueryException;
import org.testfx.service.query.NodeQuery;
import ru.raidan.books.db.model.library.tables.pojos.Book;
import ru.raidan.books.ui.main.Settings;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.raidan.books.operations.InpxTestData.book1;
import static ru.raidan.books.operations.InpxTestData.book11;
import static ru.raidan.books.operations.InpxTestData.book12;
import static ru.raidan.books.operations.InpxTestData.book2;
import static ru.raidan.books.operations.InpxTestData.book3;
import static ru.raidan.books.operations.InpxTestData.book4;
import static ru.raidan.books.operations.InpxTestData.book5;
import static ru.raidan.books.operations.InpxTestData.prepare;

@Timeout(30)
@Slf4j
@ExtendWith(ApplicationExtension.class)
class MainAppTest {

    private final String noImage = "image/No-Image-Placeholder.svg.png";
    private final String noAnnotation = "No annotation";

    @TempDir
    private Path tempDir;

    private Path inpxPath;
    private Path databasePath;
    private Path exportPath;
    private MainApp app;

    @Start
    private void start(Stage stage) throws IOException {
        inpxPath = tempDir.resolve("library").resolve("test.inpx");
        databasePath = tempDir.resolve("storage");
        exportPath = tempDir.resolve("books-export");

        var settings = Settings.builder()
                .lastDatabasePath(databasePath)
                .exportBooksPath(exportPath)
                .bookReaderArgs(new String[]{"/bin/bash", "-c"})
                .build();

        app = new MainApp(settings, false);
        app.configureMainStage(stage);
    }

    @Stop
    private void stop() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void fullTest(FxRobot robot) throws IOException {
        // Can't really test it due to Ubuntu bug with focusing new windows - new window just does not focus!
        testEmptyLibrary(robot);

        //
        // testQuit(robot);

        importLibrary(robot);

        testConfiguredLibrary(robot);
    }

    private void testEmptyLibrary(FxRobot robot) {
        waitForReady(robot);

        //
        closeEmptyLibraryWarning(robot);

        assertNull(robot.lookup("#treeBooks").queryAs(TreeTableView.class).getRoot());
        assertTrue(robot.lookup("#listAuthors").queryAs(ListView.class).getItems().isEmpty());

        //
        testAbout(robot);
    }

    @SuppressWarnings("unchecked")
    private void testConfiguredLibrary(FxRobot robot) {
        var languages = robot.lookup("#comboLang").queryAs(ComboBox.class);
        robot.clickOn(languages);

        var listAuthors = robot.lookup("#listAuthors").queryAs(ListView.class);
        robot.clickOn(listAuthors);
        waitForValue(listAuthors.getSelectionModel().selectedItemProperty(), null);

        var bookTree = (TreeTableView<Book>) robot.lookup("#treeBooks").queryAs(TreeTableView.class);
        waitForCondition("No book selected", () -> bookTree.getRoot() == null);

        listAuthors.getSelectionModel().select(1);
        waitForCondition("Books selected", () -> bookTree.getRoot() != null &&
                "[3] Books".equals(bookTree.getRoot().getValue().getTitle()));
        testAuthor1(robot);


        listAuthors.getSelectionModel().select(0);
        waitForCondition("Books selected", () -> bookTree.getRoot() != null &&
                "[4] Books".equals(bookTree.getRoot().getValue().getTitle()));
        testAuthor2(robot);


        testSearchAndRepeatSelection(robot);
    }

    @SuppressWarnings("unchecked")
    private void testAuthor1(FxRobot robot) {
        var bookTree = (TreeTableView<Book>) robot.lookup("#treeBooks").queryAs(TreeTableView.class);

        var root = bookTree.getRoot();
        assertEquals(groupBook("Books", "ru", book1(), book2(), book3()), root.getValue());

        var children = root.getChildren();
        assertEquals(2, children.size());

        var series1 = children.getFirst();
        assertEquals(groupBook("Серия 1", "ru", book1(), book2()), series1.getValue());
        assertEquals(2, series1.getChildren().size());

        assertEquals(book1(), series1.getChildren().get(0).getValue());
        assertEquals(book2(), series1.getChildren().get(1).getValue());

        var book3 = children.get(1);
        assertEquals(book3(), book3.getValue());

        var preview = robot.lookup("#imageBook").queryAs(ImageView.class);
        var annotation = robot.lookup("#viewDescription").queryAs(TextArea.class);

        waitForNullImage(preview.imageProperty());
        waitForValue(annotation.textProperty(), "");

        bookTree.getSelectionModel().select(root);
        waitForNullImage(preview.imageProperty());
        waitForValue(annotation.textProperty(), "");

        bookTree.getSelectionModel().select(book3);
        waitForNoImage(preview.imageProperty());
        waitForValue(annotation.textProperty(), noAnnotation);

        bookTree.getSelectionModel().select(series1);
        series1.setExpanded(true);

        bookTree.getSelectionModel().select(series1.getChildren().getFirst());
        waitForSomeImage(preview.imageProperty()); // blue
        waitForValue(annotation.textProperty(), "Описание 46e118ea-0203-4c8c-9d12-6f9c293f883d");

        assertEquals(0,
                executeQuery(() -> app.getController().getLibrary().getCache().getHistory(99).size()));

        // Export some book
        robot.doubleClickOn(bookTree);
        waitForCondition("Wait for history record", () ->
                executeQuery(() -> app.getController().getLibrary().getCache().getHistory(99).size()) == 1);

        bookTree.getSelectionModel().select(series1.getChildren().get(1));
        waitForNoImage(preview.imageProperty());
        waitForValue(annotation.textProperty(), noAnnotation);
    }

    @SuppressWarnings("unchecked")
    private void testAuthor2(FxRobot robot) {
        var bookTree = (TreeTableView<Book>) robot.lookup("#treeBooks").queryAs(TreeTableView.class);

        var root = bookTree.getRoot();
        assertEquals(groupBook("Books", "", book4(), book5(), book11(), book12()), root.getValue());

        var children = root.getChildren();
        assertEquals(4, children.size());

        assertEquals(book4(), children.get(0).getValue());
        assertEquals(book5(), children.get(1).getValue());
        assertEquals(book11(), children.get(2).getValue());
        assertEquals(book12(), children.get(3).getValue());

        var preview = robot.lookup("#imageBook").queryAs(ImageView.class);
        var annotation = robot.lookup("#viewDescription").queryAs(TextArea.class);

        bookTree.getSelectionModel().select(root);
        waitForNullImage(preview.imageProperty());
        waitForValue(annotation.textProperty(), "");

        bookTree.getSelectionModel().select(children.getFirst()); // 4
        waitForNoImage(preview.imageProperty());
        waitForValue(annotation.textProperty(), "Описание cfa9446c-be0e-46b8-aad7-2dc8541e1121");

        bookTree.getSelectionModel().select(children.get(1)); // 5
        waitForSomeImage(preview.imageProperty()); // read
        waitForValue(annotation.textProperty(), "Описание 646aac28-6b4f-4d85-9007-c56366a12783");

        bookTree.getSelectionModel().select(children.get(2)); // 11
        waitForNoImage(preview.imageProperty());
        waitForValue(annotation.textProperty(), "Описание 047590a3-791e-403b-ac5f-3ccc431e25e1");

        bookTree.getSelectionModel().select(children.get(3)); // 12
        waitForNoImage(preview.imageProperty());
        waitForValue(annotation.textProperty(), noAnnotation);

        // Select again
        bookTree.getSelectionModel().select(children.get(1)); // 5
        waitForSomeImage(preview.imageProperty()); // read
        waitForValue(annotation.textProperty(), "Описание 646aac28-6b4f-4d85-9007-c56366a12783");
    }

    @SuppressWarnings("unchecked")
    private void testSearchAndRepeatSelection(FxRobot robot) {
        var listAuthors = robot.lookup("#listAuthors").queryAs(ListView.class);
        var bookTree = (TreeTableView<Book>) robot.lookup("#treeBooks").queryAs(TreeTableView.class);
        var preview = robot.lookup("#imageBook").queryAs(ImageView.class);
        var annotation = robot.lookup("#viewDescription").queryAs(TextArea.class);

        var searchAuthors = robot.lookup("#fieldSearch").queryAs(TextField.class);
        searchAuthors.setText("Имя1");
        waitFor(listAuthors.getItems(), list -> list.size() == 1);

        listAuthors.getSelectionModel().select(0);
        waitForCondition("Books selected", () -> bookTree.getRoot() != null &&
                "[3] Books".equals(bookTree.getRoot().getValue().getTitle()));

        // Make sure we selected book we tried last time
        waitFor(bookTree.getSelectionModel().selectedItemProperty(), item ->
                item != null && Objects.equals(book2(), item.getValue()));
        waitForNoImage(preview.imageProperty());
        waitForValue(annotation.textProperty(), noAnnotation);

        // Deselect query
        searchAuthors.setText("");
        waitFor(listAuthors.getItems(), list -> list.size() == 2);

        waitForCondition("Books selected", () -> {
            listAuthors.getSelectionModel().select(0);
            return bookTree.getRoot() != null &&
                    "[4] Books".equals(bookTree.getRoot().getValue().getTitle());
        });

        // Make sure we selected book we tried last time
        waitFor(bookTree.getSelectionModel().selectedItemProperty(), item ->
                item != null && Objects.equals(book5(), item.getValue()));
        waitForSomeImage(preview.imageProperty()); // read
        waitForValue(annotation.textProperty(), "Описание 646aac28-6b4f-4d85-9007-c56366a12783");
    }

    private Book groupBook(String title, String lang, Book... books) {
        var book = new Book();
        book.setTitle("[" + books.length + "] " + title);
        book.setLang(lang);
        book.setSize(Stream.of(books).mapToLong(Book::getSize).sum());
        return book;
    }

    @SuppressWarnings("unchecked")
    private void importLibrary(FxRobot robot) throws IOException {
        waitForReady(robot);

        prepare(inpxPath);
        Import.importWithoutUI(inpxPath, databasePath);

        Platform.runLater(() -> app.openLibrary());

        var view = robot.lookup("#listAuthors").queryAs(ListView.class);
        waitFor(view.getItems(), list -> list.size() == 2);

        var languages = robot.lookup("#comboLang").queryAs(ComboBox.class);
        waitFor(languages.getItems(), items -> items.size() == 3); // <null>, en, ru
    }

    private void testAbout(FxRobot robot) {
        var menuHelp = waitForNode(() -> robot.lookup(".menu").lookup("Help"), MenuBarButton.class);
        robot.clickOn(menuHelp);

        var menuAbout = waitForNode(() -> robot.lookup("#menuAbout"), ContextMenuContent.MenuItemContainer.class);
        robot.clickOn(menuAbout);

        waitForNode(() -> robot.lookup("#aboutPage"), VBox.class);
        var button = waitForNode(() -> robot.lookup(".button").lookup("OK"), Button.class);

        assertEquals(Versions.APP_TITLE, robot.lookup("#labelAppName").queryAs(Label.class).getText());
        assertEquals(Versions.APP_VERSION, robot.lookup("#labelAppVersion").queryAs(Label.class).getText());

        waitForNodeDelete(() -> {
            robot.clickOn(button);
            return robot.lookup("#aboutPage");
        });
    }

    // DOES NOT WORK!@
    private void testQuit(FxRobot robot) {
        var menuFile = waitForNode(() -> robot.lookup(".menu").lookup("File"), MenuBarButton.class);
        robot.clickOn(menuFile);

        var menuQuit = waitForNode(() -> robot.lookup("#menuQuit"), ContextMenuContent.MenuItemContainer.class);
        robot.clickOn(menuQuit);
    }

    //

    private void waitForReady(FxRobot robot) {
        waitForDatabase(robot);

        var label = waitForNode(() -> robot.lookup("#labelStatus"), Label.class);
        waitForValue(label.textProperty(), "Ready");
    }

    private void waitForDatabase(FxRobot robot) {
        var label = waitForNode(() -> robot.lookup("#labelDatabase"), Label.class);
        waitForValue(label.textProperty(), "Library: [" + databasePath + "]");
    }

    private void closeEmptyLibraryWarning(FxRobot robot) {
        var dialogPane = waitForNode(() -> robot.lookup(".dialog-pane"), DialogPane.class);
        assertEquals("Unexpected application state", dialogPane.getHeaderText());
        assertEquals("Unable to find library in database, please re-import the library", dialogPane.getContentText());

        var button = waitForNode(() -> robot.lookup(".button").lookup("OK"), Button.class);
        waitForNodeDelete(() -> {
            robot.clickOn(button);
            return robot.lookup(".dialog-pane");
        });
    }

    @SuppressWarnings("BusyWait")
    private <T extends Node> T waitForNode(Supplier<NodeQuery> query, Class<T> clazz) {
        while (!Thread.currentThread().isInterrupted()) {
            var nodeQuery = query.get();
            try {
                return nodeQuery.queryAs(clazz);
            } catch (EmptyNodeQueryException e) {
                log.info("Node query is not ready yes: {}", nodeQuery);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
        throw new AssertionError("Unable to wait for " + query);
    }

    @SuppressWarnings("BusyWait")
    private void waitForNodeDelete(Supplier<NodeQuery> query) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                var node = query.get().query();
                log.info("Node still available: {}", node);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            } catch (EmptyNodeQueryException e) {
                return;
            }
        }
    }

    @SuppressWarnings("BusyWait")
    private void waitForCondition(String description, Supplier<Boolean> condition) {
        while (!Thread.currentThread().isInterrupted()) {
            if (condition.get()) {
                return;
            }
            log.info("Condition is not ready yet: {}", description);
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }
        throw new AssertionError("Unable to wait for condition: " + description);
    }

    private void waitForNullImage(ObservableValue<Image> observableValue) {
        waitForValue(observableValue, null);
    }

    private void waitForNoImage(ObservableValue<Image> observableValue) {
        waitFor(observableValue, image -> image != null && image.getUrl() != null &&
                image.getUrl().endsWith(noImage));
    }

    private void waitForSomeImage(ObservableValue<Image> observableValue) {
        waitFor(observableValue, image -> image != null && image.getUrl() == null);
    }

    private <T> void waitForValue(ObservableValue<T> observableValue, T waitFor) {
        waitFor(observableValue, test -> Objects.equals(test, waitFor));
    }

    private <T> void waitFor(ObservableValue<T> observableValue, Predicate<T> waitFor) {
        log.info("[{}]", observableValue);
        var latch = new CountDownLatch(1);
        var changeListener = new ChangeListener<T>() {
            @Override
            public void changed(ObservableValue<? extends T> observable, T oldValue, T newValue) {
                if (waitFor.test(newValue)) {
                    log.info("[{}] Ready, newValue={}", observableValue, newValue);
                    latch.countDown();
                } else {
                    log.info("[{}] Waiting, newValue={}", observableValue, newValue);
                }
            }
        };

        observableValue.addListener(changeListener);
        try {
            var newValue = observableValue.getValue();
            if (waitFor.test(newValue)) {
                log.info("[{}] Ready, newValue={}", observableValue, newValue);
                return;
            }
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException("Unable to wait for value", e);
            }
        } finally {
            observableValue.removeListener(changeListener);
        }
    }

    private <T> void waitFor(ObservableList<T> observableValue, Predicate<ObservableList<? extends T>> waitFor) {
        log.info("[{}] waitFor", observableValue);
        var latch = new CountDownLatch(1);
        var changeListener = new ListChangeListener<T>() {
            @Override
            public void onChanged(Change<? extends T> change) {
                if (waitFor.test(change.getList())) {
                    log.info("[{}] Ready", observableValue);
                    latch.countDown();
                } else {
                    log.info("[{}] Waiting", observableValue);
                }
            }
        };

        observableValue.addListener(changeListener);
        try {
            if (waitFor.test(observableValue)) {
                log.info("[{}] Ready", observableValue);
                return;
            }
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException("Unable to wait for value", e);
            }
        } finally {
            observableValue.removeListener(changeListener);
        }
    }

    private <T> T executeQuery(Supplier<T> query) {
        var future = new CompletableFuture<T>();
        app.getController().getAppExecutor().runAsync(() -> future.complete(query.get()));
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void test(Supplier<?> action) {
        try {
            log.info(" >> {}", action.get());
        } catch (Exception e) {
            //
        }
    }

}