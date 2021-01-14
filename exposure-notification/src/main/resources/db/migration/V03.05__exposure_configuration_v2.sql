create extension if not exists hstore schema public;
create table en.exposure_configuration_v2 (
  version int primary key generated always as identity,
  report_type_weights numeric array[3] not null,
  infectiousness_weights numeric array[2] not null,
  attenuation_bucket_threshold_db numeric array[3] not null,
  attenuation_bucket_weights numeric array[4] not null,
  days_since_exposure_threshold int not null,
  minimum_window_score numeric not null,
  days_since_onset_to_infectiousness_none int array not null,
  days_since_onset_to_infectiousness_standard int array not null,
  days_since_onset_to_infectiousness_high int array not null,
  infectiousness_when_dsos_missing varchar(20) not null,
  available_countries varchar(2)[] not null
);
