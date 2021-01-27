-- As a repeatable migration, this will be re-run whenever the file changes
insert into en.exposure_configuration
  (minimum_risk_score, attenuation_scores, days_since_last_exposure_scores, duration_scores, transmission_risk_scores, duration_at_attenuation_thresholds, duration_at_attenuation_weights, exposure_risk_duration, participating_countries)
select * from (
    values
      (72::int,
       '{ 1, 2, 3, 4, 5, 6, 7, 8 }'::int array[8],
       '{ 1, 1, 1, 1, 1, 1, 1, 1 }'::int array[8],
       '{ 1, 2 ,2, 4, 5, 5, 7, 8 }'::int array[8],
       '{ 0, 0, 4, 6, 6, 7, 8, 0 }'::int array[8],
       '{ 50, 70 }'::int array[2],
       '{ 1.0, 0.5, 0.0 }'::decimal(3,2) array[3],
       15::int,
       '{ BE, BG, CZ, DK, DE, EE, IE, GR, ES, FR, HR, IT, CY, LV, LT, LU, HU, MT, NL, AT, PL, PT, RO, SI, SK, SE, IS, NO, LI, CH, GB }'::varchar(2)[])
) as default_values
-- Don't insert a new version if the latest one is identical
except (
  select
    minimum_risk_score, attenuation_scores, days_since_last_exposure_scores, duration_scores, transmission_risk_scores, duration_at_attenuation_thresholds, duration_at_attenuation_weights, exposure_risk_duration, participating_countries
  from en.exposure_configuration
  order by version desc limit 1
);
