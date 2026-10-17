-- Photos in forum posts and comments. They stay out of the cabin's photo gallery and go with what they're in when it's deleted.
alter table photo add column forum_post_id bigint references forum_post (id) on delete cascade;
alter table photo add column forum_reply_id bigint references forum_reply (id) on delete cascade;
create index photo_forum_post_idx on photo (forum_post_id);
create index photo_forum_reply_idx on photo (forum_reply_id);

-- A comment can be just a photo.
alter table forum_reply alter column body drop not null;
