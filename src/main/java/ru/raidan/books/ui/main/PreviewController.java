package ru.raidan.books.ui.main;

import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import lombok.extern.slf4j.Slf4j;
import ru.raidan.books.db.model.library.tables.pojos.Book;
import ru.raidan.books.ui.state.AppExecutor;
import ru.raidan.books.ui.state.LibraryContainer;
import ru.raidan.books.ui.state.LibraryListener;
import ru.raidan.books.util.PojoUtils;

import java.io.ByteArrayInputStream;
import java.util.Objects;

@Slf4j
public class PreviewController implements LibraryListener {

    private final AppExecutor appExecutor;
    private final StatusListener statusListener;
    private final ImageView imageBook;
    private final TextArea viewDescription;

    private final Image noImagePlaceholder;
    private final String noAnnotationAvailable;

    private LibraryContainer library;

    PreviewController(Controller controller) {
        this.appExecutor = controller.getAppExecutor();
        this.statusListener = controller.getStatusListener();
        this.imageBook = Objects.requireNonNull(controller.imageBook);
        this.viewDescription = Objects.requireNonNull(controller.viewDescription);

        this.noImagePlaceholder = new Image("image/No-Image-Placeholder.svg.png");
        this.noAnnotationAvailable = "No annotation";

        clearPreview();
    }

    @Override
    public void onLibraryLoaded(LibraryContainer library) {
        this.library = library;

        clearPreview();
    }

    public void clearPreview() {
        imageBook.setImage(null);
        viewDescription.setText("");
    }

    public void showPreview(Book book) {
        appExecutor.loadAsync(
                () -> library.getAnnotationsService().loadBook(book),
                () -> statusListener.setStatus("Loading book: " + PojoUtils.toFullName(book)),
                details -> {
                    var cache = details.bookCache();
                    if (cache.getPicture() != null) {
                        imageBook.setImage(new Image(new ByteArrayInputStream(cache.getPicture())));
                    } else {
                        imageBook.setImage(noImagePlaceholder);
                    }
                    var annotation = cache.getAnnotationPlain();
                    if (annotation != null && !annotation.isBlank()) {
                        viewDescription.setText(annotation);
                    } else {
                        viewDescription.setText(noAnnotationAvailable);
                    }
                    statusListener.setReady();
                }
        );
    }
}
