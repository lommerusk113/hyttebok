-- Who may use the cabin. Owners can invite people and approve requests; system admins (ADMIN_EMAILS) manage everything.
create table membership (
    user_id    bigint primary key references app_user (id) on delete cascade,
    role       text        not null check (role in ('OWNER', 'MEMBER')),
    created_at timestamptz not null default now()
);

-- Invitation links: anyone who opens one and logs in becomes a member, until it expires or is revoked.
create table invitation (
    id         bigserial primary key,
    token      text        not null unique,
    created_by bigint      references app_user (id) on delete set null,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,
    revoked    boolean     not null default false,
    uses       int         not null default 0
);

-- "Be om tilgang": waits for an owner or admin to approve or decline.
create table access_request (
    id         bigserial primary key,
    user_id    bigint      not null references app_user (id) on delete cascade,
    message    text,
    status     text        not null default 'PENDING' check (status in ('PENDING', 'APPROVED', 'DENIED')),
    created_at timestamptz not null default now(),
    decided_by bigint      references app_user (id) on delete set null,
    decided_at timestamptz
);
