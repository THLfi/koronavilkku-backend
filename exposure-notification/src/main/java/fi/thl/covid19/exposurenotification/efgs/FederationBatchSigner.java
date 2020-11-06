package fi.thl.covid19.exposurenotification.efgs;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.*;
import java.util.Base64;

@Component
public class FederationBatchSigner {

    private static final String DIGEST_ALGORITHM = "SHA1withRSA";

    private final String keyStorePath;
    private final char[] keyStorePassword;
    private final String keyStoreKeyAlias;
    private final KeyStore keyStore;

    public FederationBatchSigner(
            @Value("${covid19.federation-gateway.signing-key-store.path}") String keyStorePath,
            @Value("${covid19.federation-gateway.signing-key-store.password}") String keyStorePassword,
            @Value("${covid19.federation-gateway.signing-key-store.key-alias}") String keyStoreKeyAlias
    ) {
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword.toCharArray();
        this.keyStoreKeyAlias = keyStoreKeyAlias;
        this.keyStore = loadKeyStore();
    }

    public String sign(final byte[] data) {
        try {
            return sign(data, keyStore);
        } catch (Exception e) {
            throw new IllegalStateException("EFGS batch signing failed.", e);
        }
    }

    private KeyStore loadKeyStore() {
        try (FileInputStream fileInputStream = new FileInputStream(ResourceUtils.getFile(keyStorePath))) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(fileInputStream, keyStorePassword);
            return keyStore;
        } catch (KeyStoreException | NoSuchAlgorithmException | IOException | CertificateException e) {
            throw new IllegalStateException("EFGS signing certificate load error.", e);
        }
    }

    private String sign(final byte[] data, KeyStore keyStore)
            throws Exception {
        PrivateKey key = (PrivateKey) keyStore.getKey(keyStoreKeyAlias, keyStorePassword);
        X509Certificate cert = (X509Certificate) keyStore.getCertificate(keyStoreKeyAlias);
        final CMSSignedDataGenerator signedDataGenerator = new CMSSignedDataGenerator();
        signedDataGenerator.addSignerInfoGenerator(createSignerInfo(cert, key));
        signedDataGenerator.addCertificate(createCertificateHolder(cert));
        CMSSignedData singedData = signedDataGenerator.generate(new CMSProcessableByteArray(data), false);
        return Base64.getEncoder().encodeToString(singedData.getEncoded());
    }

    private SignerInfoGenerator createSignerInfo(X509Certificate cert, PrivateKey key) throws OperatorCreationException,
            CertificateEncodingException {
        return new JcaSignerInfoGeneratorBuilder(createDigestBuilder()).build(createContentSigner(key), cert);
    }

    private X509CertificateHolder createCertificateHolder(X509Certificate cert) throws CertificateEncodingException,
            IOException {
        return new X509CertificateHolder(cert.getEncoded());
    }

    private DigestCalculatorProvider createDigestBuilder() throws OperatorCreationException {
        return new JcaDigestCalculatorProviderBuilder().build();
    }

    private ContentSigner createContentSigner(PrivateKey privateKey) throws OperatorCreationException {
        return new JcaContentSignerBuilder(DIGEST_ALGORITHM).build(privateKey);
    }
}
