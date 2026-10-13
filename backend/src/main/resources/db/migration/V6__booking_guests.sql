-- Number of people on a stay. Notes that were just a number (the old hint said "number of people") move over.
alter table booking add column guests integer check (guests > 0);
update booking set guests = note::integer, note = null where note ~ '^\s*[0-9]{1,3}\s*$';
