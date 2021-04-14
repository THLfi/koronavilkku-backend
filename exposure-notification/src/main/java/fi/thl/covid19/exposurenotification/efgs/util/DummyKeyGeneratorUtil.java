package fi.thl.covid19.exposurenotification.efgs.util;

import fi.thl.covid19.exposurenotification.diagnosiskey.TemporaryExposureKey;

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
import static fi.thl.covid19.exposurenotification.efgs.util.DsosMapperUtil.DsosInterpretationMapper.calculateDsos;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Comparator.comparing;

public class DummyKeyGeneratorUtil {

    public static final int BATCH_MIN_SIZE = 200;
    private static final int MIN_DAYS = 2;
    private static final int MAX_DAYS = 10;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static List<TemporaryExposureKey> concatDummyKeys(List<TemporaryExposureKey> actualKeys, List<TemporaryExposureKey> dummyKeys) {
        if (actualKeys.isEmpty() || actualKeys.size() >= BATCH_MIN_SIZE) {
            return actualKeys;
        } else {
            return Stream.concat(
                    actualKeys.stream(),
                    dummyKeys.stream()
            ).sorted(comparing(TemporaryExposureKey::getKeyData)).collect(Collectors.toList());
        }
    }

    public static List<TemporaryExposureKey> generateDummyKeys(int totalCount, boolean consentToShare, int intervalV2, Instant now) {
        List<TemporaryExposureKey> dummyKeys = new ArrayList<>();
        while (dummyKeys.size() < totalCount) {
            LocalDate symptomsOnset = now.atOffset(ZoneOffset.UTC).toLocalDate().minusDays(SECURE_RANDOM.nextInt(MAX_DAYS - MIN_DAYS + 1) + MIN_DAYS);
            for (int dummySetCount = 0; dummySetCount < 14; dummySetCount++) {
                dummyKeys.add(generateDummyKey(dummySetCount, symptomsOnset, now, consentToShare, intervalV2));
            }
        }

        return dummyKeys;
    }

    private static TemporaryExposureKey generateDummyKey(int rollingStartIntervalOffset, LocalDate symptomsOnset, Instant now, boolean consentToShare, int intervalV2) {
        byte[] keyData = new byte[16];
        SECURE_RANDOM.nextBytes(keyData);

        int rollingStartInterval = dayFirst10MinInterval(now.minus(rollingStartIntervalOffset, DAYS));

        return new TemporaryExposureKey(
                Base64.getEncoder().encodeToString(keyData),
                getRiskBucket(symptomsOnset, utcDateOf10MinInterval(rollingStartInterval)),
                rollingStartInterval,
                144,
                Set.of(),
                calculateDsos(symptomsOnset, rollingStartInterval),
                DEFAULT_ORIGIN_COUNTRY,
                consentToShare,
                Optional.empty(),
                fromV2to24hourInterval(intervalV2),
                intervalV2
        );
    }
}
