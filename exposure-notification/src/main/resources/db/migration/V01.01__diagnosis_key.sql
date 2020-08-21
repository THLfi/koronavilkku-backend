create table en.diagnosis_key (
    key_data varchar(30) primary key,
    rolling_period int not null,
    rolling_start_interval_number int not null,
    transmission_risk_level int not null,
    submission_interval int not null
);
