-- Posts get a title, who was there and when; photos now belong to a post (many per post).
alter table log_entry add column title text;
alter table log_entry add column participants text[] not null default '{}';
alter table log_entry add column start_date date;
alter table log_entry add column end_date date;
alter table log_entry alter column content drop not null;

alter table photo add column log_entry_id bigint references log_entry (id) on delete set null;
update photo p set log_entry_id = l.id from log_entry l where l.photo_id = p.id;
alter table log_entry drop column photo_id;
