-- Where the cabin is, for the weather forecast. Filled in from the address on first use; editable.
alter table cabin_info add column latitude double precision;
alter table cabin_info add column longitude double precision;
