create table suggestion (
    id          bigserial primary key,
    title       text        not null,
    description text,
    category    text        not null,
    author      text,
    voters      text[]      not null default '{}',
    done        boolean     not null default false,
    created_at  timestamptz not null default now()
);
