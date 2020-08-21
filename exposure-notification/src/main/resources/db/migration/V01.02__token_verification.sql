create table en.token_verification (
    verification_id int primary key,
    request_checksum varchar(32),
    verification_time timestamptz not null default now()
);
