-- One row per device that turned on notifications, with the topics it wants.
create table push_subscription (
    id         bigserial primary key,
    endpoint   text        not null unique,
    p256dh     text        not null,
    auth       text        not null,
    name       text,
    lang       text        not null default 'nb',
    topics     text[]      not null default '{}',
    created_at timestamptz not null default now()
);

-- The server's VAPID key pair, generated on first start (subscriptions are tied to it).
create table vapid_key (
    id          bigint primary key,
    public_key  text not null,
    private_key text not null
);
