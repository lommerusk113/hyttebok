-- The cabin's wiki: pages of know-how (how the water works, where things are), also read by the chatbot.
create table wiki_page (
    id         bigserial primary key,
    title      text        not null,
    content    text,
    updated_by text,
    updated_at timestamptz not null default now(),
    cabin_id   bigint      not null references cabin_info (id) on delete cascade
);
create index wiki_page_cabin_idx on wiki_page (cabin_id);
