-- Who changed what about access: roles, removals, invitations, deleted cabins, password resets. Names are copied
-- in, so the log still reads right after someone renames or deletes their account.
create table audit_event (
    id          bigserial primary key,
    cabin_id    bigint      references cabin_info (id) on delete cascade,
    actor_id    bigint      references app_user (id) on delete set null,
    actor_name  text,
    action      text        not null,
    target_id   bigint      references app_user (id) on delete set null,
    target_name text,
    detail      text,
    created_at  timestamptz not null default now()
);
create index audit_event_cabin on audit_event (cabin_id, created_at desc);
create index audit_event_created on audit_event (created_at desc);
