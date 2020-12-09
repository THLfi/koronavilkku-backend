package fi.thl.covid19.exposurenotification.efgs;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import static java.util.Objects.requireNonNull;

@Component
public class DbSchemaCheckHealthIndicator implements HealthIndicator {

    private final OperationDao operationDao;

    public DbSchemaCheckHealthIndicator(OperationDao operationDao) {
        this.operationDao = requireNonNull(operationDao);
    }

    @Override
    public Health health() {
        if (operationDao.checkEnSchemaExists()) {
            return Health.up().build();
        } else {
            return Health.down().withDetail("Db schema en does not exists or unspecified db query error", "FAILED").build();
        }
    }
}
