-- People who have logged in with Google. Identified by Google's stable account id (sub), not the email.
create table app_user (
    id            bigserial primary key,
    google_sub    text        not null unique,
    email         text        not null,
    name          text,
    picture       text,
    created_at    timestamptz not null default now(),
    last_login_at timestamptz not null default now()
);

-- Server-side secrets generated on first use (e.g. the key that signs login cookies).
create table app_secret (
    name  text primary key,
    value text not null
);
