create table if not exists ARCHIVE
(
    ARCHIVE_ID INT NOT NULL primary key,
    ARCHIVE_NAME TEXT NOT NULL
) STRICT;

create table if not exists BOOK
(
    BOOK_ID INT NOT NULL primary key,
    TITLE TEXT NOT NULL,
    SERIES TEXT NOT NULL,
    SERIES_NUMBER INT NULL,
    SIZE INT NOT NULL,
    DATE TEXT NOT NULL,
    LANG TEXT NOT NULL,
    FILE_NAME TEXT NOT NULL,
    FILE_INDEX TEXT NOT NULL,
    KEYWORDS TEXT NOT NULL,
    ARCHIVE_ID INT NOT NULL,
    ARCHIVE_MATCHED INT NOT NULL,

    foreign key (ARCHIVE_ID) references ARCHIVE (ARCHIVE_ID)
) STRICT;
create index if not exists IDX_BOOKS_TITLE on BOOK (TITLE);
create index if not exists IDX_BOOKS_SERIES on BOOK (SERIES);

create table if not exists GENRE
(
    GENRE_ID INT NOT NULL primary key,
    GENRE TEXT NOT NULL,
    TOTAL_BOOKS INT NOT NULL DEFAULT 0 -- Post calculated
) STRICT;

create table if not exists BOOK_TO_GENRE
(
    BOOK_ID INT NOT NULL,
    GENRE_ID INT NOT NULL,

    primary key (BOOK_ID, GENRE_ID),
    foreign key (BOOK_ID) references BOOK (BOOK_ID),
    foreign key (GENRE_ID) references GENRE (GENRE_ID)
) STRICT;

create table if not exists LANGUAGE
(
    LANG TEXT NOT NULL primary key,
    TOTAL_BOOKS INT NOT NULL
) STRICT;