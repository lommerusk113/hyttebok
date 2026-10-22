-- Bumped on "log out everywhere" and on a new password: login cookies with an older number stop working.
alter table app_user add column session_version int not null default 0;

-- One-time codes someone saves for themselves, to set a new password without asking anyone. Only hashes are kept.
create table recovery_code (
    id        bigserial primary key,
    user_id   bigint not null references app_user (id) on delete cascade,
    code_hash text   not null unique,
    used_at   timestamptz
);
create index recovery_code_user on recovery_code (user_id);
