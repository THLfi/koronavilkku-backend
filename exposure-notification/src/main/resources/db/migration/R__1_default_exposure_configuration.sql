-- As a repeatable migration, this will be re-run whenever the file changes
insert into en.exposure_configuration
  (minimum_risk_score, attenuation_scores, days_since_last_exposure_scores, duration_scores, transmission_risk_scores, duration_at_attenuation_thresholds)
select * from (
    values
      (126::int,
       '{ 1, 3, 4, 4, 6, 7, 7, 8 }'::int array[8],
       '{ 1, 1, 1, 1, 1, 1, 1, 1 }'::int array[8],
       '{ 1, 2 ,2, 4, 6, 6, 7, 8 }'::int array[8],
       '{ 0, 2, 4, 6, 6, 7, 8, 0 }'::int array[8],
       '{ 50, 70 }'::int array[2])
) as default_values
-- Don't insert a new version if the latest one is identical
except (
  select
    minimum_risk_score, attenuation_scores, days_since_last_exposure_scores, duration_scores, transmission_risk_scores, duration_at_attenuation_thresholds
  from en.exposure_configuration
  order by version desc limit 1
);
