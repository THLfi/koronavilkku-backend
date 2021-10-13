alter table en.exposure_configuration_v2 add end_of_life_reached boolean not null default 'false';
alter table en.exposure_configuration_v2 add end_of_life_statistics_fi hstore not null default '';
alter table en.exposure_configuration_v2 add end_of_life_statistics_sv hstore not null default '';
alter table en.exposure_configuration_v2 add end_of_life_statistics_en hstore not null default '';
