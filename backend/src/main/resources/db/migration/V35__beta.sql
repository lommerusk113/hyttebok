-- The example cabin: one cabin anyone can look around in, read-only, without logging in. Set by a system admin.
alter table cabin_info add column public_example boolean not null default false;
create unique index cabin_info_one_example on cabin_info (public_example) where public_example;

-- Invitations: the role they give, and how many times they work (null = any number, until they expire).
alter table invitation add column role text not null default 'MEMBER' check (role in ('OWNER', 'MEMBER'));
alter table invitation add column max_uses int check (max_uses > 0);

-- "Gi tilbakemelding": what users tell us, for the admin console.
create table feedback (
    id         bigserial primary key,
    user_id    bigint      references app_user (id) on delete set null,
    user_name  text,
    cabin_id   bigint      references cabin_info (id) on delete set null,
    page       text,
    message    text        not null,
    created_at timestamptz not null default now(),
    handled    boolean     not null default false
);

-- AI chat cost per person and month (owners see each person's share, never amounts), and the highest budget
-- warning already pushed to the owners this month (80 or 100), so each goes out once.
create table chat_usage_person (
    cabin_id bigint         not null references cabin_info (id) on delete cascade,
    month    date           not null,
    person   text           not null,
    cost_usd numeric(12, 6) not null default 0,
    primary key (cabin_id, month, person)
);
alter table chat_usage add column warned int not null default 0;
