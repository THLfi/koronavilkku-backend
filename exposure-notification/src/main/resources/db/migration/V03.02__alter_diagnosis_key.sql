alter table en.diagnosis_key add origin varchar(2) not null default 'FI';
alter table en.diagnosis_key add visited_countries varchar(2)[] not null default '{}';
alter table en.diagnosis_key add days_since_onset_of_symptoms int;
alter table en.diagnosis_key add consent_to_share boolean not null default 'false';
alter table en.diagnosis_key add efgs_sync timestamptz;
alter table en.diagnosis_key add retry_count int not null default 0;

