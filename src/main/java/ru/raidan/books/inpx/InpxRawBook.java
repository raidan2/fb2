package ru.raidan.books.inpx;

import lombok.Builder;
import lombok.Value;
import lombok.With;

import javax.annotation.Nullable;

@Value
@Builder
public class InpxRawBook {

    String author;
    String genres;
    String title;
    String series;
    @Nullable
    Integer seriesNumber;
    String fileName;
    long size;
    int bookId;
    boolean deleted;
    String fileExt;
    String date;
    String lang;
    String index;
    String keywords;
    String archive;
    @With
    boolean archiveMatched;
}
