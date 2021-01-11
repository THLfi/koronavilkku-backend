package fi.thl.covid19.exposurenotification.configuration.v2;

import fi.thl.covid19.exposurenotification.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

@RestController
@RequestMapping("/exposure/configuration/v2")
public class ExposureConfigurationV2Controller {

    private static final Logger LOG = LoggerFactory.getLogger(ExposureConfigurationV2Controller.class);

    private final ConfigurationService configurationService;
    private final Duration cacheDuration;

    public ExposureConfigurationV2Controller(ConfigurationService configurationService,
                                             @Value("${covid19.diagnosis.response-cache.config-duration}") Duration cacheDuration) {
        this.configurationService = requireNonNull(configurationService);
        this.cacheDuration = requireNonNull(cacheDuration);
    }

    @GetMapping
    public ResponseEntity<ExposureConfigurationV2> getConfiguration(@RequestParam("previous") Optional<Integer> version) {
        LOG.info("Fetching exposure configuration: {}", keyValue("previous", version));
        ExposureConfigurationV2 latest = configurationService.getLatestV2ExposureConfig();
        return version.isEmpty() || latest.version > version.get()
                ? ResponseEntity.ok().cacheControl(cacheControl(version, latest.version)).body(latest)
                : ResponseEntity.noContent().cacheControl(CacheControl.noCache()).build();
    }

    private CacheControl cacheControl(Optional<Integer> previous, int current) {
        return previous.isEmpty() || previous.get() == current || previous.get() == current-1
                ? CacheControl.maxAge(cacheDuration).cachePublic()
                : CacheControl.noCache();
    }
}
