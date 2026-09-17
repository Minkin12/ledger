create table account
(
    id          bigserial primary key,
    external_id text        not null unique,
    created_at  timestamptz not null default now()
);

create table transfer
(
    id              bigserial primary key,
    idempotency_key text        not null unique,
    request_hash    bytea       not null,
    reason          text        not null,
    created_at      timestamptz not null default now()
);

create table entry
(
    id          bigserial primary key,
    transfer_id bigint      not null references transfer (id),
    account_id  bigint      not null references account (id),
    amount      bigint      not null check (amount <> 0),
    created_at  timestamptz not null default now()
);

create index entry_account_idx on entry (account_id, id);

create table outbox
(
    id           bigserial primary key,
    event_id     uuid not null unique,
    subject      text not null,
    payload      text not null,
    published_at timestamptz
);

create index outbox_unpublished_idx on outbox (id) where published_at is null;

create table processed_event
(
    event_id     uuid primary key,
    processed_at timestamptz not null default now()
);

create table account_balance
(
    account_id    bigint primary key references account (id),
    balance       bigint not null,
    last_entry_id bigint not null default 0
);