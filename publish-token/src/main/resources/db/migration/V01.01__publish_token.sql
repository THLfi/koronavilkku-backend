create table pt.publish_token (
    id int primary key generated always as identity,
    token varchar(20) not null unique,
    created_at timestamptz not null,
    valid_through timestamptz not null,
    symptoms_onset date not null,
    origin_service varchar(100) not null,
    origin_user varchar(50) not null
);
