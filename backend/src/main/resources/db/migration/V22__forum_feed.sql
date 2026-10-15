-- Feed-style forum: a post can be just text (no title), and posts and comments get reactions.
alter table forum_post alter column title drop not null;

-- One reaction per person on a post (reply_id null) or on a comment (post_id null).
create table forum_reaction (
    id         bigserial primary key,
    post_id    bigint      references forum_post (id) on delete cascade,
    reply_id   bigint      references forum_reply (id) on delete cascade,
    emoji      text        not null,
    author     text        not null,
    created_at timestamptz not null default now(),
    cabin_id   bigint      not null references cabin_info (id) on delete cascade,
    check ((post_id is null) <> (reply_id is null))
);
create unique index forum_reaction_post_unique on forum_reaction (post_id, lower(author)) where post_id is not null;
create unique index forum_reaction_reply_unique on forum_reaction (reply_id, lower(author)) where reply_id is not null;
create index forum_reaction_cabin_idx on forum_reaction (cabin_id);
