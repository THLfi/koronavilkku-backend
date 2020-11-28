package fi.thl.covid19.exposurenotification.efgs.signing;

import fi.thl.covid19.proto.EfgsProto;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Base64;

import static fi.thl.covid19.exposurenotification.efgs.util.SignatureHelperUtil.generateBytesForSignature;

public class SigningUtil {

    private static final String DIGEST_ALGORITHM = "SHA256with";

    public static String signBatch(EfgsProto.DiagnosisKeyBatch data, PrivateKey key, X509Certificate cert)
            throws Exception {
        CMSSignedDataGenerator signedDataGenerator = new CMSSignedDataGenerator();
        signedDataGenerator.addSignerInfoGenerator(createSignerInfo(cert, key));
        signedDataGenerator.addCertificate(createCertificateHolder(cert));
        CMSSignedData singedData = signedDataGenerator.generate(new CMSProcessableByteArray(generateBytesForSignature(data.getKeysList())), false);
        return Base64.getEncoder().encodeToString(singedData.getEncoded());
    }

    private static SignerInfoGenerator createSignerInfo(X509Certificate cert, PrivateKey key) throws OperatorCreationException,
            CertificateEncodingException {
        return new JcaSignerInfoGeneratorBuilder(createDigestBuilder()).build(createContentSigner(key), cert);
    }

    private static X509CertificateHolder createCertificateHolder(X509Certificate cert) throws CertificateEncodingException,
            IOException {
        return new X509CertificateHolder(cert.getEncoded());
    }

    private static DigestCalculatorProvider createDigestBuilder() throws OperatorCreationException {
        return new JcaDigestCalculatorProviderBuilder().build();
    }

    private static ContentSigner createContentSigner(PrivateKey privateKey) throws OperatorCreationException {
        return new JcaContentSignerBuilder(DIGEST_ALGORITHM + privateKey.getAlgorithm()).build(privateKey);
    }
}
