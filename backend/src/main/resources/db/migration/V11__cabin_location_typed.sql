-- True when the weather's coordinates were typed in by hand (kept when the address changes),
-- false when they were looked up from the address.
alter table cabin_info add column location_typed boolean not null default false;
