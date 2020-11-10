alter table en.diagnosis_key add origin varchar(2) not null default '';
alter table en.diagnosis_key add visited_countries varchar(2)[] not null default '{}';
alter table en.diagnosis_key add days_since_onset_of_symptoms int not null default 0;
alter table en.diagnosis_key add consent_to_share boolean not null default 'false';
