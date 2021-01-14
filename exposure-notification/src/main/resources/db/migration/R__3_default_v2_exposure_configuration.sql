-- As a repeatable migration, this will be re-run whenever the file changes
insert into en.exposure_configuration_v2
  (report_type_weight_confirmed_test, report_type_weight_confirmed_clinical_diagnosis, report_type_weight_self_report, report_type_weight_recursive, infectiousness_weight_standard, infectiousness_weight_high, attenuation_bucket_threshold_db, attenuation_bucket_weights, days_since_exposure_threshold, minimum_window_score, days_since_onset_to_infectiousness_none, days_since_onset_to_infectiousness_standard, days_since_onset_to_infectiousness_high, infectiousness_when_dsos_missing, available_countries)
select * from (
    values(
       1.0::numeric,
       0.0::numeric,
       0.0::numeric,
       0.0::numeric,
       1.0::numeric,
       1.5::numeric,
       '{ 55, 70, 80 }'::numeric array[3],
       '{ 1.0, 1.0, 1.5, 2.5 }'::numeric array[4],
       10::int,
       1.0::numeric,
       '{ -14, -13, -12, -11, -10, -9, -8, -7, -6, -5, -4, -3, 11, 12, 13, 14 }'::int array,
       '{ -2, 4, 5, 6, 7, 8, 9, 10  }'::int array,
       '{ -1, 0, 1, 2, 3 }'::int array,
       'STANDARD',
       '{ BE, BG, CZ, DK, DE, EE, IE, GR, ES, FR, HR, IT, CY, LV, LT, LU, HU, MT, NL, AT, PL, PT, RO, SI, SK, SE, IS, NO, LI, CH, GB }'::varchar(2)[])
) as default_values
-- Don't insert a new version if the latest one is identical
except (
  select
    report_type_weight_confirmed_test, report_type_weight_confirmed_clinical_diagnosis, report_type_weight_self_report, report_type_weight_recursive, infectiousness_weight_standard, infectiousness_weight_high, attenuation_bucket_threshold_db, attenuation_bucket_weights, days_since_exposure_threshold, minimum_window_score, days_since_onset_to_infectiousness_none, days_since_onset_to_infectiousness_standard, days_since_onset_to_infectiousness_high, infectiousness_when_dsos_missing, available_countries
  from en.exposure_configuration_v2
  order by version desc limit 1
);
