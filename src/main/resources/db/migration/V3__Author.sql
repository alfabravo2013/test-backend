create table author
(
    id         serial primary key,
    full_name  text      not null,
    created_at timestamp not null default now()
);

alter table budget add column author_id int;

alter table budget add foreign key (author_id) references author(id);
