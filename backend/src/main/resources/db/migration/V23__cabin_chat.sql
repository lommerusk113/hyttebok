-- The AI chat, per cabin: off until a system admin turns it on.
alter table cabin_info add column chat_enabled boolean not null default false;
