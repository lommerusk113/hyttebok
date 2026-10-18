-- Deleting a cabin only marks it: members lose access and it disappears from their lists, but the data stays
-- and a system admin can bring it back.
alter table cabin_info add column deleted_at timestamptz;
alter table cabin_info add column deleted_by bigint references app_user (id) on delete set null;
