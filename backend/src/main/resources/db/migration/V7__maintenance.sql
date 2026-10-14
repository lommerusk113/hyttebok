create table maintenance (
    id             bigserial primary key,
    name           text        not null,
    description    text,
    interval_value integer     not null check (interval_value > 0),
    interval_unit  text        not null check (interval_unit in ('DAYS', 'WEEKS', 'MONTHS', 'YEARS')),
    last_completed date,
    created_at     timestamptz not null default now()
);

-- History, so the year overview can show what was done when.
create table maintenance_completion (
    id             bigserial primary key,
    maintenance_id bigint      not null references maintenance (id) on delete cascade,
    completed_on   date        not null,
    completed_by   text,
    created_at     timestamptz not null default now()
);
