package fi.thl.covid19.exposurenotification.efgs.util;

import fi.thl.covid19.exposurenotification.efgs.entity.AuditEntry;
import fi.thl.covid19.exposurenotification.efgs.entity.DownloadData;
import fi.thl.covid19.proto.EfgsProto;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.operator.OperatorCreationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static fi.thl.covid19.exposurenotification.efgs.util.SignatureHelperUtil.*;
import static net.logstash.logback.argument.StructuredArguments.keyValue;


public class SignatureValidationUtil {

    private static final Logger LOG = LoggerFactory.getLogger(SignatureValidationUtil.class);

    public static EfgsProto.DiagnosisKeyBatch validateSignature(
            List<AuditEntry> auditEntries, DownloadData downloadData, X509Certificate trustAnchor) {
        Optional<List<EfgsProto.DiagnosisKey>> keys = downloadData.batch.flatMap(data -> {
            AtomicInteger cursor = new AtomicInteger(0);
            List<EfgsProto.DiagnosisKey> validKeys = new ArrayList<>();
            LOG.info("Validating batch. {} {} {}",
                    keyValue("batchTag", downloadData.batchTag),
                    keyValue("auditCount", auditEntries.size()),
                    keyValue("totalKeysCount", data.getKeysCount()));
            auditEntries.forEach(audit -> {
                List<EfgsProto.DiagnosisKey> auditKeys = data.getKeysList().subList(cursor.get(), cursor.get() + Math.toIntExact(audit.amount));
                try {
                    if (checkBatchSignature(auditKeys, audit, trustAnchor)) {
                        validKeys.addAll(auditKeys);
                    } else {
                        logValidationFailed(downloadData.batchTag, audit, Optional.empty());
                    }
                } catch (CMSException | IOException | CertificateException | OperatorCreationException |
                        NoSuchAlgorithmException | NoSuchProviderException | SignatureException | InvalidKeyException e) {
                    logValidationFailed(downloadData.batchTag, audit, Optional.of(e));
                } finally {
                    LOG.info("Sub-batch processed. {} {} {} {}",
                            keyValue("batchTag", downloadData.batchTag),
                            keyValue("cursor", cursor.addAndGet(Math.toIntExact(audit.amount))),
                            keyValue("country", audit.country),
                            keyValue("amount", audit.amount));
                }
            });
            return Optional.of(validKeys);
        });
        return EfgsProto.DiagnosisKeyBatch.newBuilder().addAllKeys(keys.orElse(List.of())).build();
    }

    private static void logValidationFailed(String batchTag, AuditEntry auditEntry, Optional<Exception> e) {
        LOG.warn("Batch validation failed. {} {} {} {}",
                keyValue("batchTag", batchTag),
                keyValue("exception", e.toString()),
                keyValue("country", auditEntry.country),
                keyValue("amount", auditEntry.amount));
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
                verifySignedDataCertificate(signedData, signerInfo, audit) &&
                verifySignerInfo(signerInfo, cert) &&
                verifyOperatorSignature(audit, trustAnchor) &&
                keys.stream().allMatch(key -> key.getOrigin().equals(country));
    }

    private static boolean verifySignedDataCertificate(CMSSignedData signedData, SignerInformation signerInfo, AuditEntry audit)
            throws IOException, NoSuchAlgorithmException {
        X509CertificateHolder certFromSignedData = (X509CertificateHolder) signedData.getCertificates().getMatches(signerInfo.getSID()).iterator().next();
        return getCertThumbprint(certFromSignedData).equals(audit.uploaderSigningThumbprint);
    }

    private static boolean verifySignerInfo(SignerInformation signerInfo, X509CertificateHolder signerCert)
            throws CertificateException, OperatorCreationException, CMSException {
        return signerInfo.verify(createSignerInfoVerifier(signerCert));
    }

    private static SignerInformationVerifier createSignerInfoVerifier(X509CertificateHolder signerCert)
            throws OperatorCreationException, CertificateException {
        return new JcaSimpleSignerInfoVerifierBuilder().build(signerCert);
    }

    private static boolean verifyOperatorSignature(AuditEntry audit, X509Certificate trustAnchor)
            throws InvalidKeyException, NoSuchAlgorithmException, SignatureException, NoSuchProviderException {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        Signature signature = Signature.getInstance(trustAnchor.getSigAlgName(), "BC");
        signature.initVerify(trustAnchor.getPublicKey());
        signature.update(audit.signingCertificate.getBytes());
        return signature.verify(base64ToBytes(audit.signingCertificateOperatorSignature));
    }
}
