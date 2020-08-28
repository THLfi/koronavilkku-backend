package fi.thl.covid19.exposurenotification.diagnosiskey;

import fi.thl.covid19.exposurenotification.diagnosiskey.v1.TemporaryExposureKey;
import fi.thl.covid19.exposurenotification.tokenverification.PublishTokenVerification;
import fi.thl.covid19.exposurenotification.tokenverification.PublishTokenVerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.*;
import static fi.thl.covid19.exposurenotification.diagnosiskey.TransmissionRiskBuckets.getRiskBucket;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

@Service
public class DiagnosisKeyService {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosisKeyService.class);

    private static final Duration MAX_KEY_AGE = Duration.ofDays(14);

    private final DiagnosisKeyDao dao;
    private final PublishTokenVerificationService tokenVerificationService;

    public DiagnosisKeyService(DiagnosisKeyDao dao, PublishTokenVerificationService tokenVerificationService) {
        this.dao = requireNonNull(dao, "DAO required");
        this.tokenVerificationService = requireNonNull(tokenVerificationService, "Token verification service required");
        LOG.info("Initialized");
    }

    public void handlePublishRequest(String publishToken, List<TemporaryExposureKey> keys) {
        Instant now = Instant.now();
        List<TemporaryExposureKey> filtered = filter(keys, now);
        PublishTokenVerification verification = tokenVerificationService.getVerification(publishToken);
        int currentInterval = IntervalNumber.to24HourInterval(now);
        LOG.info("Publish token verified: {} {} {} {} {}",
                keyValue("currentInterval", currentInterval),
                keyValue("filterStart", verification.symptomsOnset.toString()),
                keyValue("filterEnd", now.toString()),
                keyValue("postedCount", keys.size()),
                keyValue("filteredCount", filtered.size()));
        dao.addKeys(verification.id, checksum(keys), currentInterval, adjustRiskBuckets(filtered, verification.symptomsOnset));
    }

    private String checksum(List<TemporaryExposureKey> keys) {
        byte[] bytes = keys.stream().map(k -> k.keyData).collect(Collectors.joining()).getBytes(UTF_8);
        return DigestUtils.md5DigestAsHex(bytes);
    }

    private List<TemporaryExposureKey> adjustRiskBuckets(List<TemporaryExposureKey> keys, LocalDate symptomsOnset) {
        return keys.stream().map(k -> adjustRiskBucket(k, symptomsOnset)).collect(Collectors.toList());
    }

    private TemporaryExposureKey adjustRiskBucket(TemporaryExposureKey key, LocalDate symptomsOnset) {
        // The symptoms onset date is local while the key validity is an UTC date
        // The least inaccurate comparison is to ignore the timezone, making accuracy 24h+<offset>
        return new TemporaryExposureKey(
                key.keyData,
                getRiskBucket(symptomsOnset, utcDateOf10MinInterval(key.rollingStartIntervalNumber)),
                key.rollingStartIntervalNumber,
                key.rollingPeriod);
    }

    List<TemporaryExposureKey> filter(List<TemporaryExposureKey> original, Instant now) {
        int minInterval = dayFirst10MinInterval(now.minus(MAX_KEY_AGE));
        int maxInterval = dayLast10MinInterval(now);
        return original.stream().filter(key -> isBetween(key, minInterval, maxInterval)).collect(Collectors.toList());
    }

    private boolean isBetween(TemporaryExposureKey key, int minPeriod, int maxPeriod) {
        return key.rollingStartIntervalNumber >= minPeriod &&
                key.rollingStartIntervalNumber + key.rollingPeriod - 1 <= maxPeriod;
    }
}
