package fi.thl.covid19.exposurenotification.efgs;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
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

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Component
public class FederationBatchSigner {

    private static final String DIGEST_ALGORITHM = "SHA256with";

    private final String keyStorePath;
    private final char[] keyStorePassword;
    private final String keyStoreKeyAlias;
    private final KeyStore keyStore;

    public FederationBatchSigner(
            @Value("${covid19.federation-gateway.signing-key-store.path}") String keyStorePath,
            @Value("${covid19.federation-gateway.signing-key-store.password}") String keyStorePassword,
            @Value("${covid19.federation-gateway.signing-key-store.key-alias}") String keyStoreKeyAlias
    ) throws Exception {
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword.toCharArray();
        this.keyStoreKeyAlias = keyStoreKeyAlias;
        this.keyStore = initKeystore();
    }

    public String sign(final byte[] data) {
        try {
            return sign(data, keyStore);
        } catch (Exception e) {
            throw new IllegalStateException("EFGS batch signing failed.", e);
        }
    }

    private KeyStore initKeystore() throws Exception {
        if (keyStorePath.isBlank()) {
            return initDevKeyStore();
        } else {
            return loadKeyStore();
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
        return new JcaContentSignerBuilder(DIGEST_ALGORITHM + privateKey.getAlgorithm()).build(privateKey);
    }

    private KeyStore initDevKeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null);
        KeyPair keyPair = generateKeyPair();
        X509Certificate certificate = generateDevCertificate(keyPair);
        keyStore.setCertificateEntry(keyStoreKeyAlias, certificate);
        keyStore.setKeyEntry(keyStoreKeyAlias, keyPair.getPrivate(), keyStorePassword, new Certificate[] { certificate });

        return keyStore;
    }

    private KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(512);
        return keyGen.generateKeyPair();
    }

    private X509Certificate generateDevCertificate(KeyPair keyPair) throws OperatorCreationException, IOException, CertificateException {
        X500Name subject = new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.C, "FI")
                .addRDN(BCStyle.CN, "koronavilkku.dev")
                .addRDN(BCStyle.O, "koronavilkku dev")
                .build();

        X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(123456789),
                Date.from(Instant.now()),
                Date.from(Instant.now().plus(Duration.ofDays(100))),
                subject,
                SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded()));
        JcaContentSignerBuilder builder = new JcaContentSignerBuilder(DIGEST_ALGORITHM + "RSA");
        ContentSigner signer = builder.build(keyPair.getPrivate());

        byte[] certBytes = certBuilder.build(signer).getEncoded();
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        return (X509Certificate)certificateFactory.generateCertificate(new ByteArrayInputStream(certBytes));
    }
}
