create table en.exposure_configuration (
  version int primary key generated always as identity,
  minimum_risk_score int not null,
  attenuation_scores int array[8] not null,
  days_since_last_exposure_scores int array[8] not null,
  duration_scores int array[8] not null,
  transmission_risk_scores int array[8] not null,
  duration_at_attenuation_thresholds int array[2] not null,
  change_time timestamptz not null default now()
);
