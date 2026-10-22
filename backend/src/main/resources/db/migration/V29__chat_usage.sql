-- What the AI chat has cost per cabin and month, to stop it at the monthly cap.
create table chat_usage (
    cabin_id bigint        not null references cabin_info (id) on delete cascade,
    month    date          not null,
    cost_usd numeric(12, 6) not null default 0,
    primary key (cabin_id, month)
);
