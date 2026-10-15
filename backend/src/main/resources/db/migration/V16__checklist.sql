-- Things that should always be at the cabin (toilet paper, soap, ...). "Mangler" puts one on the shopping list.
create table checklist_item (
    id         bigserial primary key,
    name       text        not null,
    created_at timestamptz not null default now()
);
create unique index checklist_item_name_unique on checklist_item (lower(name));
