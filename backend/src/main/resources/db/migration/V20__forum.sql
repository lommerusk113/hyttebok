-- Forum per cabin: posts, and replies to them.
create table forum_post (
    id         bigserial primary key,
    title      text        not null,
    body       text,
    author     text,
    created_at timestamptz not null default now(),
    cabin_id   bigint      not null references cabin_info (id) on delete cascade
);
create index forum_post_cabin_idx on forum_post (cabin_id);

create table forum_reply (
    id         bigserial primary key,
    post_id    bigint      not null references forum_post (id) on delete cascade,
    body       text        not null,
    author     text,
    created_at timestamptz not null default now(),
    cabin_id   bigint      not null references cabin_info (id) on delete cascade
);
create index forum_reply_post_idx on forum_reply (post_id);
create index forum_reply_cabin_idx on forum_reply (cabin_id);
