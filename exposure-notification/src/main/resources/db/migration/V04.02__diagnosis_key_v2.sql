alter table en.diagnosis_key add submission_interval_v2 int not null default 0;
alter table en.diagnosis_key alter column submission_interval_v2 drop default;
