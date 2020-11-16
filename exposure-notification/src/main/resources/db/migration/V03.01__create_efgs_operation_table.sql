create type en.state_t as enum('STARTED', 'FINISHED', 'ERROR');
create type en.direction_t as enum('INBOUND', 'OUTBOUND');

create table en.efgs_operation (
    id bigint primary key generated always as identity,
    state en.state_t not null,
    direction en.direction_t not null,
    keys_count_total int,
    keys_count_201 int,
    keys_count_409 int,
    keys_count_500 int,
    batch_tag varchar(100),
    updated_at timestamptz not null
);
