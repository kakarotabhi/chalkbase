-- School (tenant) registry.
create table school (
    id         uuid         not null,
    code       varchar(32)  not null,
    name       varchar(200) not null,
    board      varchar(16)  not null,
    city       varchar(100),
    state      varchar(100),
    active     boolean      not null default true,
    created_at timestamp    not null,
    constraint pk_school primary key (id),
    constraint uq_school_code unique (code)
);
