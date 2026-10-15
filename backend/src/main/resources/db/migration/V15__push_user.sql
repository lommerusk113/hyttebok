-- Which logged-in user a device belongs to, so access requests can go to owners and admins only.
alter table push_subscription add column user_id bigint references app_user (id) on delete set null;
