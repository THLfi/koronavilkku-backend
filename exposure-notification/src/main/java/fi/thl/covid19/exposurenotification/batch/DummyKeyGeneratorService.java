package fi.thl.covid19.exposurenotification.batch;

import fi.thl.covid19.exposurenotification.diagnosiskey.DiagnosisKeyService;
import fi.thl.covid19.exposurenotification.diagnosiskey.TemporaryExposureKey;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static fi.thl.covid19.exposurenotification.diagnosiskey.DiagnosisKeyService.DEFAULT_ORIGIN_COUNTRY;
import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.*;
import static fi.thl.covid19.exposurenotification.diagnosiskey.TransmissionRiskBuckets.getRiskBucket;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

@Service
public class DummyKeyGeneratorService {

    private final SecureRandom secureRandom;
    private final int minDays;
    private final int maxDays;
    private final int batchMinSize;

    private final DiagnosisKeyService diagnosisKeyService;


    public DummyKeyGeneratorService(DiagnosisKeyService diagnosisKeyService) {
        this.secureRandom = new SecureRandom();
        this.minDays = 2;
        this.maxDays = 10;
        this.batchMinSize = 200;
        this.diagnosisKeyService = requireNonNull(diagnosisKeyService);
    }

    public List<TemporaryExposureKey> addDummyKeysWhenNecessary(List<TemporaryExposureKey> actualKeys) {
        if (actualKeys.isEmpty() || actualKeys.size() >= batchMinSize) {
            return actualKeys;
        } else {
            return Stream.concat(
                    actualKeys.stream(),
                    generateDummyKeys(batchMinSize - actualKeys.size()).stream()
            ).sorted(comparing(TemporaryExposureKey::getKeyData)).collect(Collectors.toList());
        }
    }

    private List<TemporaryExposureKey> generateDummyKeys(int count) {
        List<TemporaryExposureKey> dummyKeys = new ArrayList<>();
        for (int totalCount = 0; totalCount < count; totalCount++) {
            Instant now = Instant.now();
            LocalDate symptomsOnset = now.atOffset(ZoneOffset.UTC).toLocalDate().minusDays(secureRandom.nextInt(maxDays - minDays + 1) + minDays);
            for (int dummySetCount = 0; dummySetCount < 14; dummySetCount++) {
                dummyKeys.add(generateDummyKey(dummySetCount, symptomsOnset, now));
            }
        }
        return dummyKeys;
    }

    private TemporaryExposureKey generateDummyKey(int rollingStartIntervalOffset, LocalDate symptomsOnset, Instant now) {
        byte[] keyData = new byte[16];
        secureRandom.nextBytes(keyData);

        int rollingStartInterval = dayFirst10MinInterval(now.minus(rollingStartIntervalOffset, DAYS));

        return new TemporaryExposureKey(
                Base64.getEncoder().encodeToString(keyData),
                getRiskBucket(symptomsOnset, utcDateOf10MinInterval(rollingStartInterval)),
                rollingStartInterval,
                144,
                Set.of(),
                diagnosisKeyService.calculateDsos(symptomsOnset, rollingStartInterval),
                DEFAULT_ORIGIN_COUNTRY,
                true,
                Optional.empty(),
                to24HourInterval(now),
                toV2Interval(now)
        );
    }
}
