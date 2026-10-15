-- Several cabins. cabin_info is the cabin; every piece of cabin data gets a cabin_id, and what exists today
-- belongs to cabin 1. Users, passwords and server secrets stay shared.

-- New cabins get ids from a sequence (cabin 1 was inserted by hand).
create sequence cabin_info_id_seq owned by cabin_info.id;
select setval('cabin_info_id_seq', coalesce((select max(id) from cabin_info), 1));
alter table cabin_info alter column id set default nextval('cabin_info_id_seq');

-- The secret code people type to ask for access ("Be om tilgang"); never listed anywhere.
alter table cabin_info add column join_code text;
update cabin_info set join_code = upper(substr(md5(random()::text || id::text), 1, 8));
alter table cabin_info alter column join_code set not null;
create unique index cabin_info_join_code_unique on cabin_info (join_code);
alter table cabin_info add column created_at timestamptz not null default now();

-- Cabin data: everything existing belongs to cabin 1.
do $$
declare t text;
begin
    foreach t in array array['booking', 'log_entry', 'photo', 'shopping_item', 'checklist_item', 'todo', 'maintenance',
                             'maintenance_completion', 'suggestion', 'note', 'push_subscription', 'invitation', 'access_request']
    loop
        execute format('alter table %I add column cabin_id bigint not null default 1 references cabin_info (id) on delete cascade', t);
        execute format('alter table %I alter column cabin_id drop default', t);
        execute format('create index %I on %I (cabin_id)', t || '_cabin_idx', t);
    end loop;
end $$;

-- Unique within a cabin, not across all of them.
drop index checklist_item_name_unique;
create unique index checklist_item_name_unique on checklist_item (cabin_id, lower(name));
-- A device can follow several cabins, each with its own topics.
alter table push_subscription drop constraint push_subscription_endpoint_key;
create unique index push_subscription_endpoint_cabin_unique on push_subscription (endpoint, cabin_id);

-- Roles per cabin: owner of one, member of another.
alter table membership add column cabin_id bigint not null default 1 references cabin_info (id) on delete cascade;
alter table membership alter column cabin_id drop default;
alter table membership drop constraint membership_pkey;
alter table membership add column id bigserial primary key;
create unique index membership_user_cabin_unique on membership (user_id, cabin_id);
