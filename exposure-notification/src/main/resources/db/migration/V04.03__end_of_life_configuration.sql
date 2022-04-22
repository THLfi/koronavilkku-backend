alter table en.exposure_configuration_v2 add end_of_life_reached boolean not null default 'false';
alter table en.exposure_configuration_v2 add end_of_life_statistics jsonb not null default '[]';
