package fi.thl.covid19.exposurenotification.efgs;

import com.google.protobuf.ByteString;
import com.google.protobuf.ProtocolStringList;
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
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static net.logstash.logback.argument.StructuredArguments.keyValue;

/*
 * Some parts are strictly based on efgs implementation to achieve compability.
 * See: https://github.com/eu-federation-gateway-service/efgs-federation-gateway/tree/master/src/main/java/eu/interop/federationgateway/batchsigning
 */
public class FederationGatewayBatchSignatureUtil {

    private static final Logger LOG = LoggerFactory.getLogger(FederationGatewayBatchSignatureUtil.class);

    public static EfgsProto.DiagnosisKeyBatch validateSignature(
            List<AuditEntry> auditEntries, DownloadData downloadData, X509Certificate trustAnchor) {
        Optional<List<EfgsProto.DiagnosisKey>> keys = downloadData.batch.flatMap(data -> {
            AtomicInteger cursor = new AtomicInteger(0);
            List<EfgsProto.DiagnosisKey> validKeys = new ArrayList<>();
            auditEntries.forEach(audit -> {
                List<EfgsProto.DiagnosisKey> auditKeys = data.getKeysList().subList(cursor.get(), Math.toIntExact(audit.amount));
                try {
                    if (checkBatchSignature(auditKeys, audit, trustAnchor)) {
                        validKeys.addAll(auditKeys);
                    }
                } catch (CMSException | IOException | CertificateException | OperatorCreationException |
                        NoSuchAlgorithmException | NoSuchProviderException | SignatureException | InvalidKeyException e) {
                    LOG.warn("Batch validation failed. {}", keyValue("batchTag", downloadData.batchTag));
                } finally {
                    cursor.addAndGet(Math.toIntExact(audit.amount));
                }
            });
            return Optional.of(validKeys);
        });
        return EfgsProto.DiagnosisKeyBatch.newBuilder().addAllKeys(keys.orElse(List.of())).build();
    }

    private static boolean checkBatchSignature(
            List<EfgsProto.DiagnosisKey> keys,
            AuditEntry audit,
            X509Certificate trustAnchor
    ) throws CMSException, CertificateException, OperatorCreationException, IOException, NoSuchAlgorithmException,
            NoSuchProviderException, SignatureException, InvalidKeyException {
        CMSSignedData signedData = new CMSSignedData(
                new CMSProcessableByteArray(generateBytesForSignature(keys)),
                base64ToBytes(audit.batchSignature)
        );
        SignerInformation signerInfo = signedData.getSignerInfos().getSigners().iterator().next();

        X509CertificateHolder cert = (X509CertificateHolder) new PEMParser(new StringReader(audit.signingCertificate)).readObject();
        String country = cert.getSubject().getRDNs(BCStyle.C)[0].getFirst().getValue().toString();

        return cert.isValidOn(new Date()) &&
                verifySignerInfo(signerInfo, cert) &&
                verifyOperatorSignature(audit, trustAnchor) &&
                keys.stream().allMatch(key -> key.getOrigin().equals(country));
    }

    private static byte[] base64ToBytes(String batchSignatureBase64) {
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
                .map(FederationGatewayBatchSignatureUtil::generateBytesToVerify)
                .sorted(Comparator.nullsLast(
                        Comparator.comparing(FederationGatewayBatchSignatureUtil::bytesToBase64)
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

    public static String getCertThumbprint(X509CertificateHolder x509CertificateHolder) throws IOException, NoSuchAlgorithmException {
        return calculateHash(x509CertificateHolder.getEncoded());
    }

    private static String calculateHash(byte[] data) throws NoSuchAlgorithmException {
        byte[] certHashBytes = MessageDigest.getInstance("SHA-256").digest(data);
        String hexString = new BigInteger(1, certHashBytes).toString(16);

        if (hexString.length() == 63) {
            hexString = "0" + hexString;
        }

        return hexString;
    }

    private static boolean verifyOperatorSignature(AuditEntry audit, X509Certificate trustAnchor)
            throws InvalidKeyException, NoSuchAlgorithmException, SignatureException, NoSuchProviderException {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        Signature signature = Signature.getInstance("SHA256withRSA", "BC");
        signature.initVerify(trustAnchor.getPublicKey());
        signature.update(audit.signingCertificate.getBytes());
        return signature.verify(base64ToBytes(audit.signingCertificateOperatorSignature));
    }
}