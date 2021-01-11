-- As a repeatable migration, this will be re-run whenever the file changes
insert into en.exposure_configuration_v2
  (report_type_weights, infectiousness_weights, attenuation_bucket_threshold_db, attenuation_bucket_weights, days_since_exposure_threshold, minimum_window_score, days_since_onset_to_infectiousness, available_countries)
select * from (
    values('CONFIRMED_TEST => 1.0'::hstore,
       'NONE => 0.0,
        STANDARD => 1.0,
        HIGH => 2.0
       '::hstore,
       '{ 1.0, 2.0, 3.0 }'::numeric array[3],
       '{ 0.0, 1.0, 1.5, 2.5 }'::numeric array[4],
       1::int,
       1.0::numeric,
       '-7 => NONE,
        7 => STANDARD,
        1 => HIGH
       '::hstore,
       '{ BE, BG, CZ, DK, DE, EE, IE, GR, ES, FR, HR, IT, CY, LV, LT, LU, HU, MT, NL, AT, PL, PT, RO, SI, SK, SE, IS, NO, LI, CH, GB }'::varchar(2)[])
) as default_values
-- Don't insert a new version if the latest one is identical
except (
  select
    report_type_weights, infectiousness_weights, attenuation_bucket_threshold_db, attenuation_bucket_weights, days_since_exposure_threshold, minimum_window_score, days_since_onset_to_infectiousness, available_countries
  from en.exposure_configuration_v2
  order by version desc limit 1
);
