create table en.stats_report_keys (
    id bigint primary key generated always as identity,
    reported_at timestamptz not null
);
