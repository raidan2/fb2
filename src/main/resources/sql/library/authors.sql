create table if not exists AUTHOR
(
    AUTHOR_ID INT NOT NULL primary key,
    ORIGIN TEXT NOT NULL,
    SURNAME TEXT NOT NULL,
    NAME TEXT,
    MIDDLE_NAME TEXT,
    TOTAL_BOOKS INT NOT NULL DEFAULT 0
) STRICT;
create index if not exists IDX_AUTHORS_NAME on AUTHOR (NAME);
create index if not exists IDX_AUTHORS_SURNAME on AUTHOR (SURNAME);
create index if not exists IDX_AUTHORS_MIDDLENAME on AUTHOR (MIDDLE_NAME);
create index if not exists IDX_AUTHORS_NAME on AUTHOR (ORIGIN);

create table if not exists AUTHOR_TO_BOOK
(
    AUTHOR_ID INT NOT NULL,
    BOOK_ID INT NOT NULL,

    primary key (AUTHOR_ID, BOOK_ID),
    foreign key (AUTHOR_ID) references AUTHOR (AUTHOR_ID),
    foreign key (BOOK_ID) references BOOK (BOOK_ID)
) STRICT;