package fi.thl.covid19.exposurenotification.configuration;

import fi.thl.covid19.exposurenotification.configuration.v1.ExposureConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
        String sql = "SELECT " +
                "version, " +
                "minimum_risk_score, " +
                "attenuation_scores, " +
                "days_since_last_exposure_scores, " +
                "duration_scores, " +
                "transmission_risk_scores, " +
                "duration_at_attenuation_thresholds " +
                "FROM en.exposure_configuration " +
                "ORDER BY version DESC " +
                "LIMIT 1";
        return jdbcTemplate.queryForObject(sql, Map.of(), (rs, i) -> new ExposureConfiguration(
                rs.getInt("version"),
                rs.getInt("minimum_risk_score"),
                toList(rs.getArray("attenuation_scores")),
                toList(rs.getArray("days_since_last_exposure_scores")),
                toList(rs.getArray("duration_scores")),
                toList(rs.getArray("transmission_risk_scores")),
                toList(rs.getArray("duration_at_attenuation_thresholds"))
        ));
    }

    private List<Integer> toList(Array sqlArray) throws SQLException {
        return Arrays.asList((Integer[]) sqlArray.getArray());
    }
}
