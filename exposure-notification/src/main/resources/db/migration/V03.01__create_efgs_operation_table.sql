create type en.state_t as enum('QUEUED', 'STARTED', 'FINISHED', 'ERROR');
create type en.direction_t as enum('INBOUND', 'OUTBOUND');

create table en.efgs_operation (
    id bigint primary key generated always as identity,
    state en.state_t not null default 'STARTED',
    direction en.direction_t not null,
    keys_count int,
    updated_at timestamptz not null default now()
);

alter table en.diagnosis_key add efgs_operation bigint;
alter table en.diagnosis_key add constraint fk_efgs_operation foreign key (efgs_operation) references en.efgs_operation(id);
insert into en.efgs_operation (state, direction) values ('QUEUED', 'OUTBOUND');
update en.diagnosis_key set efgs_operation=(select id from en.efgs_operation where state='QUEUED');

