-- New cabins get a join code straight away (the app replaces it with a friendlier one when it creates the cabin).
alter table cabin_info alter column join_code set default upper(substr(md5(random()::text || clock_timestamp()::text), 1, 10));
