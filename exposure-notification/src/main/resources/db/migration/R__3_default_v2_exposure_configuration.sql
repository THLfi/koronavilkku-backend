-- As a repeatable migration, this will be re-run whenever the file changes
insert into en.exposure_configuration_v2
  (report_type_weight_confirmed_test, report_type_weight_confirmed_clinical_diagnosis, report_type_weight_self_report, report_type_weight_recursive, infectiousness_weight_standard, infectiousness_weight_high, attenuation_bucket_threshold_db, attenuation_bucket_weights, days_since_exposure_threshold, minimum_window_score, minimum_daily_score, days_since_onset_to_infectiousness, infectiousness_when_dsos_missing, available_countries, end_of_life_reached, end_of_life_statistics)
select * from (
    values(
       1.0::numeric,
       0.0::numeric,
       0.0::numeric,
       0.0::numeric,
       0.625::numeric,
       1.0::numeric,
       '{ 51, 63, 70 }'::numeric array[3],
       '{ 1.25, 1.0, 0.5, 0.0 }'::numeric array[4],
       10::int,
       1.0::numeric,
       900::int,
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
       -2 => HIGH,
       -1 => HIGH,
       0 => HIGH,
       1 => HIGH,
       2 => HIGH,
       3 => HIGH,
       4 => HIGH,
       5 => HIGH,
       6 => HIGH,
       7 => HIGH,
       8 => STANDARD,
       9 => STANDARD,
       10 => STANDARD,
       11 => NONE,
       12 => NONE,
       13 => NONE,
       14 => NONE
       '::hstore,
       'HIGH',
       '{ BE, BG, CZ, DK, DE, EE, IE, GR, ES, FR, HR, IT, CY, LV, LT, LU, HU, MT, NL, AT, PL, PT, RO, SI, SK, SE, IS, NO, LI, CH, GB }'::varchar(2)[],
       false::boolean,
       '[
         {
           "value": {
             "fi": "2,5 miljoonaa",
             "sv": "2,5 miljoner",
             "en": "2.5 million"
           },
           "label": {
             "fi": "suomalaista käytti Koronavilkkua.",
             "sv": "finländare använde Coronablinkern.",
             "en": "people in Finland used Koronavilkku."
           }
         },
         {
           "value": {
             "fi": "64\u00a0000",
             "sv": "64\u00a0000",
             "en": "64,000"
           },
           "label": {
             "fi": "käyttäjää ilmoitti tartunnastaan Koronavilkun kautta.",
             "sv": "användare meddelade om sin smitta via Coronablinkern.",
             "en": "users reported their infection with Koronavilkku."
           }
         },
         {
           "value": {
             "fi": "23\u00a0%",
             "sv": "23\u00a0%",
             "en": "23\u00a0%"
           },
           "label": {
             "fi": "käyttäjistä kertoi saaneensa altistumisilmoituksen.",
             "sv": "av användarna uppgav att de hade fått exponeringsmeddelandet.",
             "en": "of users reported having received an exposure notification."
           }
         }
       ]'::jsonb)
) as default_values
-- Don't insert a new version if the latest one is identical
except (
  select
    report_type_weight_confirmed_test, report_type_weight_confirmed_clinical_diagnosis, report_type_weight_self_report, report_type_weight_recursive, infectiousness_weight_standard, infectiousness_weight_high, attenuation_bucket_threshold_db, attenuation_bucket_weights, days_since_exposure_threshold, minimum_window_score, minimum_daily_score, days_since_onset_to_infectiousness, infectiousness_when_dsos_missing, available_countries, end_of_life_reached, end_of_life_statistics
  from en.exposure_configuration_v2
  order by version desc limit 1
);
