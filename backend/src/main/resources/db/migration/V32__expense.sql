-- Shared costs. An EXPENSE is paid by one person and split evenly between some people; a SETTLEMENT is one person
-- paying another back (split_between holds the one who got the money). Amounts in øre, so sums are exact.
create table expense (
    id            bigserial primary key,
    cabin_id      bigint      not null references cabin_info (id) on delete cascade,
    kind          text        not null default 'EXPENSE' check (kind in ('EXPENSE', 'SETTLEMENT')),
    description   text,
    amount        bigint      not null check (amount > 0),
    paid_by       text        not null,
    split_between text[]      not null check (cardinality(split_between) > 0),
    spent_on      date        not null default current_date,
    created_by    text,
    created_at    timestamptz not null default now()
);
create index expense_cabin on expense (cabin_id, spent_on desc);
