package ru.raidan.books.inpx;

import ru.raidan.books.db.model.library.tables.pojos.Library;

public interface InpxListener {

    void onBegin(Library library);

    void onRecordParsed(InpxRawBook rawBook);

    void onFileComplete();

    void onComplete();
}
