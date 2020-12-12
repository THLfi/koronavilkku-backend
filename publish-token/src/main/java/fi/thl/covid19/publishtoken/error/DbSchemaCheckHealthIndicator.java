package fi.thl.covid19.publishtoken.error;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

import static java.util.Objects.requireNonNull;

@Repository
@ConditionalOnProperty(
        prefix = "covid19.db-schema-check", value = "enabled"
)
public class DbSchemaCheckHealthIndicator implements HealthIndicator {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DbSchemaCheckHealthIndicator(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = requireNonNull(jdbcTemplate);
    }

    @Override
    public Health health() {
        if (checkEnSchemaExists()) {
            return Health.up().build();
        } else {
            return Health.down().withDetail("Db schema en does not exists or unspecified db query error", "FAILED").build();
        }
    }

    private boolean checkEnSchemaExists() {
        String sql = "select count(*) from information_schema.schemata where schema_name = :schema_name";
        try {
            return jdbcTemplate.query(sql, Map.of("schema_name", "pt"),
                    (rs, i) -> rs.getInt(1)).stream().findFirst().orElseThrow(() -> new IllegalStateException("Count returned nothing.")) > 0;
        } catch (DataAccessException | IllegalStateException e) {
            return false;
        }
    }
}
