package fi.thl.covid19.publishtoken;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

import static java.util.Objects.requireNonNull;

@Service
public class MaintenanceService {
    private static final Logger LOG = LoggerFactory.getLogger(MaintenanceService.class);

    private final PublishTokenDao dao;
    private final Duration expiredTokenLifetime;

    public MaintenanceService(PublishTokenDao dao,
                              @Value("${covid19.maintenance.expired-token-lifetime}") Duration expiredTokenLifetime) {
        this.dao = requireNonNull(dao);
        this.expiredTokenLifetime = requireNonNull(expiredTokenLifetime);
        LOG.info("Initialized: expiredTokenLifetime={}", expiredTokenLifetime);
    }

    @Scheduled(initialDelayString = "${covid19.maintenance.interval}",
            fixedRateString = "${covid19.maintenance.interval}")
    public void deleteExpiredTokens() {
        Instant limit = Instant.now().minus(expiredTokenLifetime);
        LOG.info("Deleting expired tokens: limit={}", limit);
        dao.deleteTokensExpiredBefore(limit);
    }
}
