-- As a repeatable migration, this will be re-run whenever the file changes
insert into en.exposure_configuration_v2
  (report_type_weight_confirmed_test, report_type_weight_confirmed_clinical_diagnosis, report_type_weight_self_report, report_type_weight_recursive, infectiousness_weight_standard, infectiousness_weight_high, attenuation_bucket_threshold_db, attenuation_bucket_weights, days_since_exposure_threshold, minimum_window_score, days_since_onset_to_infectiousness, infectiousness_when_dsos_missing, available_countries)
select * from (
    values(
       1.0::numeric,
       0.0::numeric,
       0.0::numeric,
       0.0::numeric,
       1.0::numeric,
       1.5::numeric,
       '{ 55, 70, 80 }'::numeric array[3],
       '{ 2.0, 1.0, 0.25, 0.0 }'::numeric array[4],
       10::int,
       1.0::numeric,
       '-14 => NONE,
       -13 => NONE,
       -12 => NONE,
       -11 => NONE,
       -10 => NONE,
       -9 => NONE,
       -8 => NONE,
       -7 => NONE,
       -6 => NONE,
       -5 => NONE,
       -4 => NONE,
       -3 => NONE,
       -2 => STANDARD,
       -1 => HIGH,
       0 => HIGH,
       1 => HIGH,
       2 => HIGH,
       3 => HIGH,
       4 => STANDARD,
       5 => STANDARD,
       6 => STANDARD,
       7 => STANDARD,
       8 => STANDARD,
       9 => STANDARD,
       10 => STANDARD,
       11 => NONE,
       12 => NONE,
       13 => NONE,
       14 => NONE
       '::hstore,
       'STANDARD',
       '{ BE, BG, CZ, DK, DE, EE, IE, GR, ES, FR, HR, IT, CY, LV, LT, LU, HU, MT, NL, AT, PL, PT, RO, SI, SK, SE, IS, NO, LI, CH, GB }'::varchar(2)[])
) as default_values
-- Don't insert a new version if the latest one is identical
except (
  select
    report_type_weight_confirmed_test, report_type_weight_confirmed_clinical_diagnosis, report_type_weight_self_report, report_type_weight_recursive, infectiousness_weight_standard, infectiousness_weight_high, attenuation_bucket_threshold_db, attenuation_bucket_weights, days_since_exposure_threshold, minimum_window_score, days_since_onset_to_infectiousness, infectiousness_when_dsos_missing, available_countries
  from en.exposure_configuration_v2
  order by version desc limit 1
);
