create table pt.stats_token_create (
    id bigint primary key generated always as identity,
    created_at timestamptz not null
);

create table pt.stats_sms_send (
    id bigint primary key generated always as identity,
    sent_at timestamptz not null
);
