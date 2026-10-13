create table photo (
    id            bigserial primary key,
    file_name     text        not null,
    content_type  text        not null,
    caption       text,
    uploaded_by   text,
    created_at    timestamptz not null default now()
);

create table cabin_info (
    id             bigint primary key,
    name           text not null,
    tagline        text,
    description    text,
    address        text,
    wifi_name      text,
    wifi_password  text,
    arrival_info   text,
    departure_info text,
    hero_photo_id  bigint references photo (id) on delete set null
);

insert into cabin_info (id, name, tagline, description, arrival_info, departure_info)
values (1, 'Hytta', 'Vårt lille fristed i fjellet',
        'Skriv litt om hytta her – hvor den ligger, hvor mange sengeplasser, og hva som gjør den spesiell.',
        'Nøkkel ligger i nøkkelboksen. Slå på strøm og vann i boden.',
        'Slå av varmen, tøm søppel, lås alle dører og lukk vannet.');

create table todo (
    id          bigserial primary key,
    title       text        not null,
    description text,
    done        boolean     not null default false,
    done_by     text,
    done_at     timestamptz,
    created_by  text,
    created_at  timestamptz not null default now()
);

create table shopping_item (
    id         bigserial primary key,
    name       text        not null,
    quantity   text,
    bought     boolean     not null default false,
    bought_by  text,
    bought_at  timestamptz,
    added_by   text,
    created_at timestamptz not null default now()
);

create table log_entry (
    id         bigserial primary key,
    author     text        not null,
    content    text        not null,
    photo_id   bigint references photo (id) on delete set null,
    created_at timestamptz not null default now()
);

create table note (
    id         bigserial primary key,
    title      text        not null,
    content    text,
    color      text        not null default 'amber',
    pinned     boolean     not null default false,
    updated_at timestamptz not null default now()
);

create table booking (
    id         bigserial primary key,
    guest_name text not null,
    start_date date not null,
    end_date   date not null,
    note       text,
    check (end_date >= start_date)
);
