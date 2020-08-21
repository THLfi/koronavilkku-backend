drop index if exists en.diagnosis_key_interval;
create index diagnosis_key_interval on en.diagnosis_key(submission_interval);
