-- When a forum post or reply was last edited (null: never).
alter table forum_post add column edited_at timestamptz;
alter table forum_reply add column edited_at timestamptz;
