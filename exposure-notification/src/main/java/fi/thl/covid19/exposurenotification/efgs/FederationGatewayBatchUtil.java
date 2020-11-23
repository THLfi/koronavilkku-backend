package fi.thl.covid19.exposurenotification.efgs;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.ProtocolStringList;
import fi.thl.covid19.exposurenotification.diagnosiskey.TemporaryExposureKey;
import fi.thl.covid19.proto.EfgsProto;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.operator.OperatorCreationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.utcDateOf10MinInterval;
import static fi.thl.covid19.exposurenotification.diagnosiskey.TransmissionRiskBuckets.DEFAULT_RISK_BUCKET;
import static fi.thl.covid19.exposurenotification.diagnosiskey.TransmissionRiskBuckets.getRiskBucket;
import static fi.thl.covid19.exposurenotification.efgs.DsosMapperUtil.DEFAULT_DAYS_SINCE_SYMPTOMS;
import static fi.thl.covid19.exposurenotification.efgs.DsosMapperUtil.DsosInterpretationMapper;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

/*
 * Some parts are strictly based on efgs implementation to achieve compability.
 * See: https://github.com/eu-federation-gateway-service/efgs-federation-gateway/tree/master/src/main/java/eu/interop/federationgateway/batchsigning
 */
public class FederationGatewayBatchUtil {
    private static final Logger LOG = LoggerFactory.getLogger(FederationGatewayBatchUtil.class);

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

    public static String getBatchTag(LocalDate date, String postfix) {
        return getDateString(date) + "-" + postfix;
    }

    public static String getDateString(LocalDate date) {
        return DateTimeFormatter.ISO_DATE.format(date);
    }

    public static EfgsProto.DiagnosisKeyBatch validateSignature(List<AuditEntry> auditEntries, DownloadData downloadData) {
        Optional<List<EfgsProto.DiagnosisKey>> keys = downloadData.batch.flatMap(data -> {
            AtomicLong skip = new AtomicLong(0);
            List<EfgsProto.DiagnosisKey> validKeys = new ArrayList<>();
            auditEntries.forEach(audit -> {
                List<EfgsProto.DiagnosisKey> auditKeys = data.getKeysList().stream().skip(skip.get()).limit(audit.amount)
                        .collect(Collectors.toList());
                try {
                    if (checkBatchSignature(auditKeys, audit)) {
                        validKeys.addAll(auditKeys);
                    }
                } catch (CMSException | IOException | CertificateException | OperatorCreationException e) {
                    LOG.warn("Batch validation failed. {}", keyValue("batchTag", downloadData.batchTag));
                } finally {
                    skip.addAndGet(audit.amount);
                }
            });
            return Optional.of(validKeys);
        });
        return EfgsProto.DiagnosisKeyBatch.newBuilder().addAllKeys(keys.orElse(List.of())).build();
    }

    private static boolean checkBatchSignature(
            List<EfgsProto.DiagnosisKey> keys,
            AuditEntry audit
    ) throws CMSException, CertificateException, OperatorCreationException, IOException {
        CMSSignedData signedData = new CMSSignedData(
                new CMSProcessableByteArray(generateBytesForSignature(keys)),
                base64ToBytes(audit.batchSignature)
        );
        SignerInformation signerInfo = signedData.getSignerInfos().getSigners().iterator().next();

        X509CertificateHolder cert = (X509CertificateHolder) new PEMParser(new StringReader(audit.signingCertificate)).readObject();
        String country = cert.getSubject().getRDNs(BCStyle.C)[0].getFirst().getValue().toString();

        return cert.isValidOn(new Date()) &&
                verifySignerInfo(signerInfo, cert) &&
                keys.stream().allMatch(key -> key.getOrigin().equals(country));
    }

    private static byte[] base64ToBytes(final String batchSignatureBase64) {
        return Base64.getDecoder().decode(batchSignatureBase64.getBytes());
    }

    private static boolean verifySignerInfo(SignerInformation signerInfo, X509CertificateHolder signerCert)
            throws CertificateException, OperatorCreationException, CMSException {
        return signerInfo.verify(createSignerInfoVerifier(signerCert));
    }

    private static SignerInformationVerifier createSignerInfoVerifier(X509CertificateHolder signerCert)
            throws OperatorCreationException, CertificateException {
        return new JcaSimpleSignerInfoVerifierBuilder().build(signerCert);
    }

    public static byte[] generateBytesForSignature(List<EfgsProto.DiagnosisKey> keys) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        keys.stream()
                .map(FederationGatewayBatchUtil::generateBytesToVerify)
                .sorted(Comparator.nullsLast(
                        Comparator.comparing(FederationGatewayBatchUtil::bytesToBase64)
                ))
                .forEach(byteArrayOutputStream::writeBytes);

        return byteArrayOutputStream.toByteArray();
    }

    private static byte[] generateBytesToVerify(EfgsProto.DiagnosisKey diagnosisKey) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        writeBytesInByteArray(diagnosisKey.getKeyData(), byteArrayOutputStream);
        writeSeperatorInArray(byteArrayOutputStream);
        writeIntInByteArray(diagnosisKey.getRollingStartIntervalNumber(), byteArrayOutputStream);
        writeSeperatorInArray(byteArrayOutputStream);
        writeIntInByteArray(diagnosisKey.getRollingPeriod(), byteArrayOutputStream);
        writeSeperatorInArray(byteArrayOutputStream);
        writeIntInByteArray(diagnosisKey.getTransmissionRiskLevel(), byteArrayOutputStream);
        writeSeperatorInArray(byteArrayOutputStream);
        writeVisitedCountriesInByteArray(diagnosisKey.getVisitedCountriesList(),
                byteArrayOutputStream);
        writeSeperatorInArray(byteArrayOutputStream);
        writeB64StringInByteArray(diagnosisKey.getOrigin(), byteArrayOutputStream);
        writeSeperatorInArray(byteArrayOutputStream);
        writeIntInByteArray(diagnosisKey.getReportTypeValue(), byteArrayOutputStream);
        writeSeperatorInArray(byteArrayOutputStream);
        writeIntInByteArray(diagnosisKey.getDaysSinceOnsetOfSymptoms(), byteArrayOutputStream);
        writeSeperatorInArray(byteArrayOutputStream);
        return byteArrayOutputStream.toByteArray();
    }

    public static String bytesToBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static void writeSeperatorInArray(final ByteArrayOutputStream byteArray) {
        byteArray.writeBytes(".".getBytes(StandardCharsets.US_ASCII));
    }

    private static void writeStringInByteArray(final String batchString, final ByteArrayOutputStream byteArray) {
        byteArray.writeBytes(batchString.getBytes(StandardCharsets.US_ASCII));
    }

    private static void writeB64StringInByteArray(final String batchString, final ByteArrayOutputStream byteArray) {
        String base64String = bytesToBase64(batchString.getBytes(StandardCharsets.US_ASCII));

        if (base64String != null) {
            writeStringInByteArray(base64String, byteArray);
        }
    }

    private static void writeIntInByteArray(final int batchInt, final ByteArrayOutputStream byteArray) {
        String base64String = bytesToBase64(ByteBuffer.allocate(4).putInt(batchInt).array());

        if (base64String != null) {
            writeStringInByteArray(base64String, byteArray);
        }
    }

    private static void writeBytesInByteArray(final ByteString bytes, ByteArrayOutputStream byteArray) {
        String base64String = bytesToBase64(bytes.toByteArray());

        if (base64String != null) {
            writeStringInByteArray(base64String, byteArray);
        }
    }

    private static void writeVisitedCountriesInByteArray(final ProtocolStringList countries,
                                                         final ByteArrayOutputStream byteArray) {
        writeB64StringInByteArray(String.join(",", countries), byteArray);
    }
}
