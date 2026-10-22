-- "Jeg tar den": who said they'll do a one-off task.
alter table todo add column claimed_by text;

-- Who has which key, where the spare is, the key box code.
alter table cabin_info add column key_info text;
