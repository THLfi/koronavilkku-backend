create type state_t as enum('STARTED', 'FINISHED', 'ERROR');

create table en.efgs_update_to_operation (
    id bigint primary key generated always as identity,
    state state_t not null default 'STARTED',
    updated_at timestampz not null default now()
);

alter table en.diagnosis_key add constraint fk_efgs_update_to_operation foreign key (efgs_update_to_operation) references efgs_update_to_operation(id);
