package fi.thl.covid19.exposurenotification.efgs;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import fi.thl.covid19.exposurenotification.diagnosiskey.TemporaryExposureKey;
import fi.thl.covid19.proto.EfgsProto;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.utcDateOf10MinInterval;
import static fi.thl.covid19.exposurenotification.diagnosiskey.TransmissionRiskBuckets.DEFAULT_RISK_BUCKET;
import static fi.thl.covid19.exposurenotification.diagnosiskey.TransmissionRiskBuckets.getRiskBucket;
import static fi.thl.covid19.exposurenotification.efgs.DsosMapperUtil.DEFAULT_DAYS_SINCE_SYMPTOMS;
import static fi.thl.covid19.exposurenotification.efgs.DsosMapperUtil.DsosInterpretationMapper;

public class FederationGatewayBatchUtil {

    public static EfgsProto.DiagnosisKeyBatch transform(List<TemporaryExposureKey> localKeys) {
        List<EfgsProto.DiagnosisKey> efgsKeys = localKeys.stream()
                .filter(localKey -> localKey.consentToShareWithEfgs)
                .map(localKey ->
                EfgsProto.DiagnosisKey.newBuilder()
                        .setKeyData(ByteString.copyFrom(Base64.getDecoder().decode(localKey.keyData.getBytes())))
                        .setRollingStartIntervalNumber(localKey.rollingStartIntervalNumber)
                        .setRollingPeriod(localKey.rollingPeriod)
                        .setTransmissionRiskLevel(0x7FFFFFFF)
                        .addAllVisitedCountries(localKey.visitedCountries)
                        .setOrigin(localKey.origin)
                        .setReportType(EfgsProto.ReportType.CONFIRMED_TEST)
                        .setDaysSinceOnsetOfSymptoms(localKey.daysSinceOnsetOfSymptoms.orElse(DEFAULT_DAYS_SINCE_SYMPTOMS))
                        .build())
                .collect(Collectors.toList());

        return EfgsProto.DiagnosisKeyBatch.newBuilder().addAllKeys(efgsKeys).build();
    }

    public static List<TemporaryExposureKey> transform(EfgsProto.DiagnosisKeyBatch batch) {
        return batch.getKeysList().stream().map(remoteKey ->
                new TemporaryExposureKey(
                        new String(Base64.getEncoder().encode(remoteKey.getKeyData().toByteArray()), StandardCharsets.UTF_8),
                        calculateTransmissionRisk(remoteKey),
                        remoteKey.getRollingStartIntervalNumber(),
                        remoteKey.getRollingPeriod(),
                        new HashSet<>(remoteKey.getVisitedCountriesList()),
                        DsosInterpretationMapper.mapFrom(remoteKey.getDaysSinceOnsetOfSymptoms()),
                        remoteKey.getOrigin(),
                        true
                )).collect(Collectors.toList());
    }

    public static int calculateTransmissionRisk(EfgsProto.DiagnosisKey key) {
        LocalDate keyDate = utcDateOf10MinInterval(key.getRollingStartIntervalNumber());
        Optional<Integer> mappedDsos = DsosInterpretationMapper.mapFrom(key.getDaysSinceOnsetOfSymptoms());
        return mappedDsos.map(dsos -> getRiskBucket(LocalDate.from(keyDate).plusDays(dsos), keyDate)).orElse(DEFAULT_RISK_BUCKET);
    }

    public static byte[] serialize(EfgsProto.DiagnosisKeyBatch batch) {
        return batch.toByteArray();
    }

    public static EfgsProto.DiagnosisKeyBatch deserialize(byte[] data) {
        try {
            return EfgsProto.DiagnosisKeyBatch.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("Incorrect format from federation gateway.", e);
        }
    }

    public static String getBatchTag(Instant instant, String postfix) {
        return getDateString(instant) + "-" + postfix;
    }

    public static String getDateString(Instant date) {
        return DateTimeFormatter.ISO_DATE.format(LocalDate.ofInstant(date, ZoneOffset.UTC));
    }
}
