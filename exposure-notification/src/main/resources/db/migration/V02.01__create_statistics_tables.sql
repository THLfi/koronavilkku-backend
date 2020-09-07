create table en.stats_diagnosis_keys_created (
    id bigint primary key generated always as identity,
    created_at timestamptz not null
);
