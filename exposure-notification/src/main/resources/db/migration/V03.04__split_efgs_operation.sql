drop table efgs_operation;
drop type en.direction_t;

create table en.efgs_outbound_operation (
    id bigint primary key generated always as identity,
    state en.state_t not null,
    keys_count_total int not null default 0,
    keys_count_201 int not null default 0,
    keys_count_409 int not null default 0,
    keys_count_500 int not null default 0,
    batch_tag varchar(100),
    updated_at timestamptz not null
);

create table en.efgs_inbound_operation (
    id bigint primary key generated always as identity,
    state en.state_t not null,
    keys_count_total int not null default 0,
    invalid_signature_count int not null default 0,
    batch_tag varchar(100),
    retry_count int not null default 0,
    updated_at timestamptz not null
);
