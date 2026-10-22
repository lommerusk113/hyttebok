-- Check-in and check-out: who's actually at the cabin, not just who booked it.
alter table booking add column checked_in_at  timestamptz;
alter table booking add column checked_in_by  text;
alter table booking add column checked_out_at timestamptz;
