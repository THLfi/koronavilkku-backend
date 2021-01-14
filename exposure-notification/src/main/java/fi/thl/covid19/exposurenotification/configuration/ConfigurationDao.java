package fi.thl.covid19.exposurenotification.configuration;

import fi.thl.covid19.exposurenotification.configuration.v1.ExposureConfiguration;
import fi.thl.covid19.exposurenotification.configuration.v2.ExposureConfigurationV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

@Repository
public class ConfigurationDao {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationDao.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ConfigurationDao(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = requireNonNull(jdbcTemplate);
        LOG.info("Initialized");
    }

    @Cacheable(value = "exposure-config", sync = true)
    public ExposureConfiguration getLatestExposureConfiguration() {
        LOG.info("Fetching exposure configuration");
        String sql = "select " +
                "version, " +
                "minimum_risk_score, " +
                "attenuation_scores, " +
                "days_since_last_exposure_scores, " +
                "duration_scores, " +
                "transmission_risk_scores, " +
                "duration_at_attenuation_thresholds, " +
                "duration_at_attenuation_weights, " +
                "exposure_risk_duration, " +
                "participating_countries " +
                "from en.exposure_configuration " +
                "order by version desc " +
                "limit 1";
        return jdbcTemplate.queryForObject(sql, Map.of(), (rs, i) -> new ExposureConfiguration(
                rs.getInt("version"),
                rs.getInt("minimum_risk_score"),
                toList(rs.getArray("attenuation_scores")),
                toList(rs.getArray("days_since_last_exposure_scores")),
                toList(rs.getArray("duration_scores")),
                toList(rs.getArray("transmission_risk_scores")),
                toList(rs.getArray("duration_at_attenuation_thresholds")),
                toList(rs.getArray("duration_at_attenuation_weights")),
                rs.getInt("exposure_risk_duration"),
                Arrays.stream((String[]) rs.getArray("participating_countries").getArray()).collect(Collectors.toSet())
        ));
    }

    @Cacheable(value = "exposure-config-v2", sync = true)
    public ExposureConfigurationV2 getLatestV2ExposureConfiguration() {
        LOG.info("Fetching exposure v2 configuration");
        String sql = "select " +
                "version, " +
                "report_type_weight_confirmed_test, " +
                "report_type_weight_confirmed_clinical_diagnosis, " +
                "report_type_weight_self_report, " +
                "report_type_weight_recursive, " +
                "infectiousness_weight_standard, " +
                "infectiousness_weight_high, " +
                "attenuation_bucket_threshold_db, " +
                "attenuation_bucket_weights, " +
                "days_since_exposure_threshold, " +
                "minimum_window_score, " +
                "days_since_onset_to_infectiousness_none, " +
                "days_since_onset_to_infectiousness_standard, " +
                "days_since_onset_to_infectiousness_high, " +
                "infectiousness_when_dsos_missing, " +
                "available_countries " +
                "from en.exposure_configuration_v2 " +
                "order by version desc " +
                "limit 1";
        return jdbcTemplate.queryForObject(sql, Map.of(), (rs, i) -> new ExposureConfigurationV2(
                rs.getInt("version"),
                rs.getBigDecimal("report_type_weight_confirmed_test"),
                rs.getBigDecimal("report_type_weight_confirmed_clinical_diagnosis"),
                rs.getBigDecimal("report_type_weight_self_report"),
                rs.getBigDecimal("report_type_weight_recursive"),
                rs.getBigDecimal("infectiousness_weight_standard"),
                rs.getBigDecimal("infectiousness_weight_high"),
                toList(rs.getArray("attenuation_bucket_threshold_db")),
                toList(rs.getArray("attenuation_bucket_weights")),
                rs.getInt("days_since_exposure_threshold"),
                rs.getDouble("minimum_window_score"),
                Set.of((Integer[]) rs.getArray("days_since_onset_to_infectiousness_none").getArray()),
                Set.of((Integer[]) rs.getArray("days_since_onset_to_infectiousness_standard").getArray()),
                Set.of((Integer[]) rs.getArray("days_since_onset_to_infectiousness_high").getArray()),
                rs.getString("infectiousness_when_dsos_missing"),
                Arrays.stream((String[]) rs.getArray("available_countries").getArray()).collect(Collectors.toSet())
        ));
    }

    @SuppressWarnings("unchecked")
    private <T extends Number> List<T> toList(Array sqlArray) throws SQLException {
        return Arrays.asList((T[]) sqlArray.getArray());
    }
}
