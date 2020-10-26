create type state_t as enum('STARTED', 'FINISHED', 'ERROR');
create type direction_t as enum('INBOUND', 'OUTBOUND');

create table en.efgs_operation (
    id bigint primary key generated always as identity,
    state state_t not null default 'STARTED',
    direction direction_t not null,
    keys_count int,
    updated_at timestampz not null default now()
);

alter table en.diagnosis_key add constraint fk_efgs_operation foreign key (efgs_operation) references en.efgs_operation(id);
