-- A post can belong to a stay (booking).
alter table log_entry add column booking_id bigint references booking (id) on delete set null;
