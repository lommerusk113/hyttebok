-- Guest links: read-only arrival info (arrival, WiFi, keys, address, departure) for someone borrowing the cabin,
-- without an account. Valid through valid_until (a date), unless revoked.
create table guest_link (
    id          bigserial primary key,
    cabin_id    bigint      not null references cabin_info (id) on delete cascade,
    token       text        not null unique,
    label       text        not null,
    valid_until date        not null,
    revoked     boolean     not null default false,
    created_by  bigint      references app_user (id) on delete set null,
    created_at  timestamptz not null default now()
);
create index guest_link_cabin on guest_link (cabin_id);

-- Files on wiki pages: photos and PDFs (manuals for the heat pump, the boat ...).
create table wiki_attachment (
    id            bigserial primary key,
    cabin_id      bigint      not null references cabin_info (id) on delete cascade,
    wiki_page_id  bigint      not null references wiki_page (id) on delete cascade,
    file_name     text        not null,
    content_type  text        not null,
    original_name text        not null,
    size          bigint      not null,
    uploaded_by   text,
    created_at    timestamptz not null default now()
);
create index wiki_attachment_page on wiki_attachment (wiki_page_id);
