package fi.thl.covid19.exposurenotification.batch;

import com.google.protobuf.ByteString;
import fi.thl.covid19.proto.*;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static fi.thl.covid19.exposurenotification.efgs.util.DsosMapperUtil.DEFAULT_LOCAL_DAYS_SINCE_SYMPTOMS;
import static java.nio.charset.StandardCharsets.UTF_8;

public final class BatchFileFactory {
    private BatchFileFactory() {
    }

    public static final String BIN_HEADER = "EK Export v1";
    public static final int BIN_HEADER_LENGTH = 16;
    public static final String BIN_NAME = "export.bin";
    public static final String SIG_NAME = "export.sig";

    private static final int DEFAULT_BYTE_SIZE = 32 * 1024;

    public static byte[] createBatchFile(
            SignatureConfig signatureConfig,
            PrivateKey key,
            BatchMetadata metadata,
            List<fi.thl.covid19.exposurenotification.diagnosiskey.TemporaryExposureKey> keys) {

        if (keys.isEmpty()) throw new IllegalArgumentException("Cannot create a batch file without keys");
        try (ByteArrayOutputStream bytesOut = new ByteArrayOutputStream(DEFAULT_BYTE_SIZE);
             ZipOutputStream zipOut = new ZipOutputStream(bytesOut)) {

            byte[] binBytes = getBinFileBytes(signatureConfig, metadata, keys);
            byte[] signatureBytes = Signing.sign(signatureConfig.algorithmName, key, binBytes);

            zipOut.putNextEntry(new ZipEntry(BIN_NAME));
            zipOut.write(binBytes);
            zipOut.closeEntry();

            zipOut.putNextEntry(new ZipEntry(SIG_NAME));
            createSignatureList(signatureConfig, signatureBytes).writeTo(zipOut);
            zipOut.closeEntry();

            zipOut.finish();
            return bytesOut.toByteArray();
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Batch file creation failed", e);
        }
    }

    private static byte[] getBinFileBytes(SignatureConfig signatureConfig,
                                          BatchMetadata metadata,
                                          List<fi.thl.covid19.exposurenotification.diagnosiskey.TemporaryExposureKey> keys) throws IOException {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream(DEFAULT_BYTE_SIZE)) {
            stream.writeBytes(StringUtils.rightPad(BIN_HEADER, BIN_HEADER_LENGTH, ' ').getBytes(UTF_8));
            createKeyExport(signatureConfig, metadata, keys).writeTo(stream);
            return stream.toByteArray();
        }
    }

    private static TemporaryExposureKeyExport createKeyExport(
            SignatureConfig signatureConfig,
            BatchMetadata metadata,
            List<fi.thl.covid19.exposurenotification.diagnosiskey.TemporaryExposureKey> keys) {
        return TemporaryExposureKeyExport.newBuilder()
                .setStartTimestamp(metadata.startTimestampUtcSec)
                .setEndTimestamp(metadata.endTimestampUtcSec)
                .setRegion(metadata.region)
                .setBatchNum(1)
                .setBatchSize(1)
                .addAllKeys(keys.stream().map(BatchFileFactory::toProtoBuf).collect(Collectors.toList()))
                .addSignatureInfos(BatchFileFactory.createSignatureInfo(signatureConfig))
                .build();
    }

    private static SignatureInfo createSignatureInfo(SignatureConfig config) {
        return SignatureInfo.newBuilder()
                .setVerificationKeyVersion(config.keyVersion)
                .setVerificationKeyId(config.keyId)
                .setSignatureAlgorithm(config.algorithmOid)
                .build();
    }

    private static TEKSignatureList createSignatureList(SignatureConfig config, byte[] signatureBytes) {
        TEKSignature signature = TEKSignature.newBuilder()
                .setSignatureInfo(createSignatureInfo(config))
                .setBatchNum(1)
                .setBatchSize(1)
                .setSignature(ByteString.copyFrom(signatureBytes))
                .build();
        return TEKSignatureList.newBuilder().addSignatures(signature).build();
    }

    private static TemporaryExposureKey toProtoBuf(fi.thl.covid19.exposurenotification.diagnosiskey.TemporaryExposureKey key) {
        return TemporaryExposureKey.newBuilder()
                .setKeyData(ByteString.copyFrom(Base64.getDecoder().decode(key.keyData.getBytes(UTF_8))))
                .setTransmissionRiskLevel(key.transmissionRiskLevel)
                .setRollingStartIntervalNumber(key.rollingStartIntervalNumber)
                .setRollingPeriod(key.rollingPeriod)
                .setReportType(TemporaryExposureKey.ReportType.CONFIRMED_TEST)
                .setDaysSinceOnsetOfSymptoms(key.daysSinceOnsetOfSymptoms.orElse(DEFAULT_LOCAL_DAYS_SINCE_SYMPTOMS))
                .build();
    }
}
