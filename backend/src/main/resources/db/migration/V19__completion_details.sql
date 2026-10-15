-- When something is done: who took part, and a few words about it (both optional).
alter table maintenance_completion add column participants text[];
alter table maintenance_completion add column note text;
alter table todo add column done_with text[];
alter table todo add column done_note text;
