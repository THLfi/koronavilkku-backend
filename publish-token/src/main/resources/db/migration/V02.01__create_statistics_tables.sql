create table pt.stats_tokens_created (
    id bigint primary key generated always as identity,
    created_at timestamptz not null
);

create table pt.stats_sms_created (
    id bigint primary key generated always as identity,
    created_at timestamptz not null
);
