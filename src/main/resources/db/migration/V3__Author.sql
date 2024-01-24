create table author
(
    id         serial primary key,
    full_name  text      not null,
    created_at timestamp not null default now()
);

alter table budget add column author_id int;

alter table budget add foreign key (author_id) references author(id);

create extension if not exists pg_trgm;
create index idx_full_name_gin on author using gin (full_name gin_trgm_ops);
