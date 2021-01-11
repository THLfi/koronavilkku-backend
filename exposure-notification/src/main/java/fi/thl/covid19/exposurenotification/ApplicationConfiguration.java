package fi.thl.covid19.exposurenotification;

import org.cache2k.extra.spring.SpringCache2kCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

@EnableCaching
@Configuration
public class ApplicationConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationConfiguration.class);

    private static final Duration REST_TIMEOUT = Duration.of(10, ChronoUnit.SECONDS);
    private static final int ACTIVE_INTERVALS = 14;

    private final boolean cacheEnabled;
    private final Duration statusCacheDuration;

    public ApplicationConfiguration(@Value("${covid19.diagnosis.data-cache.enabled}") boolean cacheEnabled,
                                    @Value("${covid19.diagnosis.data-cache.status-duration}") Duration statusCacheDuration) {
        this.cacheEnabled = cacheEnabled;
        this.statusCacheDuration = requireNonNull(statusCacheDuration);
        LOG.info("Initialized: {} {}",
                keyValue("cacheEnabled", cacheEnabled),
                keyValue("statusCacheDuration", statusCacheDuration));
    }

    @Bean("default")
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(REST_TIMEOUT)
                .setReadTimeout(REST_TIMEOUT)
                .build();
    }

    @Bean
    public CacheManager cacheManager() {
        if (cacheEnabled) {
            return new SpringCache2kCacheManager().addCaches(
                    b -> b.name("exposure-config")
                            .expireAfterWrite(statusCacheDuration.toMinutes(), TimeUnit.MINUTES)
                            .entryCapacity(1),
                    b -> b.name("exposure-config-v2")
                            .expireAfterWrite(statusCacheDuration.toMinutes(), TimeUnit.MINUTES)
                            .entryCapacity(1),
                    b -> b.name("available-intervals")
                            .expireAfterWrite(statusCacheDuration.toMinutes(), TimeUnit.MINUTES)
                            .entryCapacity(1),
                    b -> b.name("key-count")
                            .expireAfterWrite(statusCacheDuration.toMinutes(), TimeUnit.MINUTES)
                            .entryCapacity(ACTIVE_INTERVALS));
        } else {
            return new NoOpCacheManager();
        }
    }
}
