package fi.thl.covid19.exposurenotification.diagnosiskey;

import fi.thl.covid19.exposurenotification.diagnosiskey.v1.DiagnosisPublishRequest;
import fi.thl.covid19.exposurenotification.diagnosiskey.v1.TemporaryExposureKeyRequest;
import fi.thl.covid19.exposurenotification.tokenverification.PublishTokenVerification;
import fi.thl.covid19.exposurenotification.tokenverification.PublishTokenVerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.*;
import static fi.thl.covid19.exposurenotification.diagnosiskey.TransmissionRiskBuckets.getRiskBucket;
import static fi.thl.covid19.exposurenotification.efgs.util.DsosMapperUtil.DsosInterpretationMapper.calculateDsos;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static net.logstash.logback.argument.StructuredArguments.keyValue;


@Service
public class DiagnosisKeyService {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosisKeyService.class);

    private static final Duration MAX_KEY_AGE = Duration.ofDays(14);
    public static final String DEFAULT_ORIGIN_COUNTRY = "FI";

    private final DiagnosisKeyDao dao;
    private final PublishTokenVerificationService tokenVerificationService;

    public DiagnosisKeyService(DiagnosisKeyDao dao, PublishTokenVerificationService tokenVerificationService) {
        this.dao = requireNonNull(dao, "DAO required");
        this.tokenVerificationService = requireNonNull(tokenVerificationService, "Token verification service required");
        LOG.info("Initialized");
    }

    public void handlePublishRequest(String publishToken, DiagnosisPublishRequest request) {
        Instant now = Instant.now();
        PublishTokenVerification verification = tokenVerificationService.getVerification(publishToken);
        List<TemporaryExposureKeyRequest> filtered = filter(request.keys, now);
        int currentInterval = to24HourInterval(now);
        int currentIntervalV2 = toV2Interval(now);
        List<TemporaryExposureKey> keys = transform(
                filtered, request.visitedCountriesSet, request.consentToShareWithEfgs, verification, currentInterval, currentIntervalV2);
        LOG.info("Publish token verified: {} {} {} {} {} {}",
                keyValue("currentInterval", currentInterval),
                keyValue("currentIntervalV2", currentIntervalV2),
                keyValue("filterStart", verification.symptomsOnset.toString()),
                keyValue("filterEnd", now.toString()),
                keyValue("postedCount", request.keys.size()),
                keyValue("filteredCount", filtered.size())
        );
        dao.addKeys(verification.id, checksum(keys), currentInterval, currentIntervalV2, keys, getExportedKeyCount(keys));
    }

    private long getExportedKeyCount(List<TemporaryExposureKey> keys) {
        return keys.stream().filter(key -> key.transmissionRiskLevel > 0 && key.transmissionRiskLevel < 7).count();
    }

    private List<TemporaryExposureKey> transform(
            List<TemporaryExposureKeyRequest> requestKeys,
            Set<String> visitedCountries,
            boolean consentToShareWithEfgs,
            PublishTokenVerification verification,
            int intervalNumber,
            int intervalNumberV2
    ) {
        return requestKeys.stream().map(requestKey -> new TemporaryExposureKey(
                requestKey.keyData,
                getRiskBucket(verification.symptomsOnset, utcDateOf10MinInterval(requestKey.rollingStartIntervalNumber)),
                requestKey.rollingStartIntervalNumber,
                requestKey.rollingPeriod,
                visitedCountries,
                calculateDsos(verification.symptomsOnset, requestKey.rollingStartIntervalNumber),
                DEFAULT_ORIGIN_COUNTRY,
                consentToShareWithEfgs,
                verification.symptomsExist,
                intervalNumber,
                intervalNumberV2
        )).collect(Collectors.toList());
    }

    private String checksum(List<TemporaryExposureKey> keys) {
        byte[] bytes = keys.stream().map(k -> k.keyData).collect(Collectors.joining()).getBytes(UTF_8);
        return DigestUtils.md5DigestAsHex(bytes);
    }

    List<TemporaryExposureKeyRequest> filter(List<TemporaryExposureKeyRequest> original, Instant now) {
        int minInterval = dayFirst10MinInterval(now.minus(MAX_KEY_AGE));
        int maxInterval = dayLast10MinInterval(now);
        return original.stream().filter(key -> isBetween(key, minInterval, maxInterval)).collect(Collectors.toList());
    }

    private boolean isBetween(TemporaryExposureKeyRequest key, int minPeriod, int maxPeriod) {
        return key.rollingStartIntervalNumber >= minPeriod &&
                key.rollingStartIntervalNumber + key.rollingPeriod - 1 <= maxPeriod;
    }
}
