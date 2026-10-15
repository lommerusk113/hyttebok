-- Email + password as an alternative to Google. A user can have either or both; one account per email.
alter table app_user alter column google_sub drop not null;
alter table app_user add column password_hash text;
create unique index app_user_email_unique on app_user (lower(email));

-- "New password" links made by an owner or admin (there is no email service to send them).
create table password_reset (
    token      text primary key,
    user_id    bigint      not null references app_user (id) on delete cascade,
    created_by bigint      references app_user (id) on delete set null,
    expires_at timestamptz not null,
    used       boolean     not null default false
);
