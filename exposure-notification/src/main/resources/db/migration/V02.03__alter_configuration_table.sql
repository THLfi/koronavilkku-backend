alter table en.exposure_configuration add duration_at_attenuation_weights decimal(3,2) array[3] not null default '{ 1.0, 0.5, 0.0 }';
alter table en.exposure_configuration alter column duration_at_attenuation_weights drop default;
alter table en.exposure_configuration add exposure_risk_duration int not null default 15;
alter table en.exposure_configuration alter column exposure_risk_duration drop default;
