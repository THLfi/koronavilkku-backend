package fi.thl.covid19.exposurenotification.batch;

import com.google.protobuf.InvalidProtocolBufferException;
import fi.thl.covid19.exposurenotification.diagnosiskey.TemporaryExposureKey;
import fi.thl.covid19.proto.SignatureInfo;
import fi.thl.covid19.proto.TEKSignature;
import fi.thl.covid19.proto.TEKSignatureList;
import fi.thl.covid19.proto.TemporaryExposureKeyExport;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.to10MinInterval;
import static org.junit.jupiter.api.Assertions.*;

public class BatchFileFactoryTest {

    @Test
    public void noEmptyZipsAreCreated() {
        KeyPair keyPair = Signing.randomKeyPair();
        SignatureConfig signatureConfig = new SignatureConfig(
                "v1",
                "test.key.id",
                "1.2.840.10045.4.3.2",
                "SHA256withECDSA");
        BatchMetadata metadata = new BatchMetadata(12345, 23456, "TEST");
        PrivateKey privateKey = keyPair.getPrivate();
        List<TemporaryExposureKey> keys = List.of();
        assertThrows(IllegalArgumentException.class,
                () -> BatchFileFactory.createBatchFile(signatureConfig, privateKey, metadata, keys));
    }

    @Test
    public void zipCreationWorks() throws IOException, GeneralSecurityException {
        KeyPair keyPair = Signing.randomKeyPair();
        SignatureConfig signatureConfig = new SignatureConfig(
                "v1",
                "test.key.id",
                "1.2.840.10045.4.3.2",
                "SHA256withECDSA");
        BatchMetadata metadata = new BatchMetadata(12345, 23456, "TEST");

        byte[] bytes = BatchFileFactory.createBatchFile(
                signatureConfig, keyPair.getPrivate(),
                metadata, List.of(createKey(1), createKey(2), createKey(3), createKey(4)));

        try (ByteArrayInputStream bytesInput = new ByteArrayInputStream(bytes);
             ZipInputStream zipInput = new ZipInputStream(bytesInput)) {

            ZipEntry entry1 = zipInput.getNextEntry();
            assertNotNull(entry1);
            assertEquals(BatchFileFactory.BIN_NAME, entry1.getName());
            byte[] entry1Bytes = extract(zipInput);
            zipInput.closeEntry();

            assertHeaderCorrect(Arrays.copyOfRange(entry1Bytes, 0, BatchFileFactory.BIN_HEADER_LENGTH));
            byte[] payload = Arrays.copyOfRange(entry1Bytes, BatchFileFactory.BIN_HEADER_LENGTH, entry1Bytes.length);
            assertKeyExportCorrect(signatureConfig, metadata, payload);

            ZipEntry entry2 = zipInput.getNextEntry();
            assertNotNull(entry2);
            assertEquals(BatchFileFactory.SIG_NAME, entry2.getName());
            byte[] entry2Bytes = extract(zipInput);
            zipInput.closeEntry();

            assertSignatureCorrect(signatureConfig, keyPair.getPublic(), entry2Bytes, entry1Bytes);
        }
    }

    private TemporaryExposureKey createKey(int seed) {
        Random rand = new Random(seed);
        byte[] keyBytes = new byte[16];
        rand.nextBytes(keyBytes);
        return new TemporaryExposureKey(
                Base64.getEncoder().encodeToString(keyBytes),
                rand.nextInt(9),
                to10MinInterval(Instant.now())-rand.nextInt(10)*6*24,
                144);
    }

    private void assertHeaderCorrect(byte[] headerBytes) {
        String header = new String(headerBytes);
        assertTrue(header.startsWith(BatchFileFactory.BIN_HEADER));
        assertEquals(BatchFileFactory.BIN_HEADER, header.trim());
    }

    private void assertKeyExportCorrect(SignatureConfig signatureConfig, BatchMetadata metadata, byte[] payload)
            throws InvalidProtocolBufferException {
        TemporaryExposureKeyExport export = TemporaryExposureKeyExport.parseFrom(payload);
        assertEquals(metadata.startTimestampUtcSec, export.getStartTimestamp());
        assertEquals(metadata.endTimestampUtcSec, export.getEndTimestamp());
        assertEquals(metadata.region, export.getRegion());
        assertEquals(1, export.getBatchNum());
        assertEquals(1, export.getBatchSize());
        assertEquals(1, export.getSignatureInfosCount());
        assertSignatureInfo(signatureConfig, export.getSignatureInfos(0));
    }

    private void assertSignatureCorrect(SignatureConfig config, PublicKey key, byte[] signatureBytes, byte[] payload)
            throws InvalidProtocolBufferException, GeneralSecurityException {
        TEKSignatureList signatureList = TEKSignatureList.parseFrom(signatureBytes);
        assertEquals(1, signatureList.getSignaturesCount());
        TEKSignature signature = signatureList.getSignatures(0);
        assertEquals(1, signature.getBatchNum());
        assertEquals(1, signature.getBatchSize());
        assertSignatureInfo(config, signature.getSignatureInfo());
        assertTrue(Signing.singatureMatches(config.algorithmName, key, signature.getSignature().toByteArray(), payload));
    }

    private void assertSignatureInfo(SignatureConfig config, SignatureInfo info) {
        assertEquals(config.keyVersion, info.getVerificationKeyVersion());
        assertEquals(config.keyId, info.getVerificationKeyId());
        assertEquals(config.algorithmOid, info.getSignatureAlgorithm());
    }

    private byte[] extract(ZipInputStream zipInput) throws IOException {
        try (ByteArrayOutputStream bytesOut = new ByteArrayOutputStream()) {
            zipInput.transferTo(bytesOut);
            return bytesOut.toByteArray();
        }
    }
}
