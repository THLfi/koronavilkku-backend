package fi.thl.covid19.exposurenotification.efgs.util;

import com.google.protobuf.ByteString;
import com.google.protobuf.ProtocolStringList;
import fi.thl.covid19.proto.EfgsProto;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/*
 * Some parts are strictly based on efgs implementation to achieve compability.
 * See: https://github.com/eu-federation-gateway-service/efgs-federation-gateway/tree/master/src/main/java/eu/interop/federationgateway/batchsigning
 */
public class SignatureHelperUtil {
    public static byte[] generateBytesForSignature(List<EfgsProto.DiagnosisKey> keys) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        keys.stream()
                .map(SignatureHelperUtil::generateBytesToVerify)
                .sorted(Comparator.nullsLast(
                        Comparator.comparing(SignatureHelperUtil::bytesToBase64)
                ))
                .forEach(byteArrayOutputStream::writeBytes);

        return byteArrayOutputStream.toByteArray();
    }

    public static String getCertThumbprint(X509CertificateHolder x509CertificateHolder) throws IOException, NoSuchAlgorithmException {
        return calculateHash(x509CertificateHolder.getEncoded());
    }

    public static String bytesToBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] base64ToBytes(String batchSignatureBase64) {
        return Base64.getDecoder().decode(batchSignatureBase64.getBytes());
    }

    public static String x509CertificateToPem(X509Certificate cert) throws IOException {
        StringWriter stringWriter = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            pemWriter.writeObject(cert);
        }
        return stringWriter.toString();
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

    private static String calculateHash(byte[] data) throws NoSuchAlgorithmException {
        byte[] certHashBytes = MessageDigest.getInstance("SHA-256").digest(data);
        String hexString = new BigInteger(1, certHashBytes).toString(16);

        if (hexString.length() == 63) {
            hexString = "0" + hexString;
        }

        return hexString;
    }

    public static String stackTraceToString(Exception e) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        e.printStackTrace(printWriter);
        return stringWriter.toString();
    }
}
