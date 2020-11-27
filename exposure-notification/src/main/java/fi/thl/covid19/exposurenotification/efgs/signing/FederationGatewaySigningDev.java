package fi.thl.covid19.exposurenotification.efgs.signing;

import fi.thl.covid19.proto.EfgsProto;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static fi.thl.covid19.exposurenotification.efgs.util.SigningUtil.signBatch;
import static java.util.Objects.requireNonNull;

@Component
@ConditionalOnProperty(
        prefix = "covid19.federation-gateway.signing-key-store", value = "implementation",
        havingValue = "dev"
)
public class FederationGatewaySigningDev implements FederationGatewaySigning {
    private static final String DIGEST_ALGORITHM = "SHA256with";
    private static final String DEV_TRUST_ANCHOR_ISSUER = "koronavilkku-dev-root";

    private final char[] keyStorePassword;
    private final String keyStoreKeyAlias;
    private final String trustAnchorAlias;
    private final KeyStore keyStore;

    public FederationGatewaySigningDev(
            @Value("${covid19.federation-gateway.signing-key-store.password}") String keyStorePassword,
            @Value("${covid19.federation-gateway.signing-key-store.key-alias}") String keyStoreKeyAlias,
            @Value("${covid19.federation-gateway.signing-key-store.trust-anchor-alias}") String trustAnchorAlias
    ) throws Exception {
        this.keyStorePassword = requireNonNull(keyStorePassword.toCharArray());
        this.keyStoreKeyAlias = requireNonNull(keyStoreKeyAlias);
        this.trustAnchorAlias = requireNonNull(trustAnchorAlias);
        this.keyStore = initKeystore();
    }

    public String sign(EfgsProto.DiagnosisKeyBatch data) {
        try {
            PrivateKey key = (PrivateKey) keyStore.getKey(keyStoreKeyAlias, keyStorePassword);
            X509Certificate cert = (X509Certificate) keyStore.getCertificate(keyStoreKeyAlias);
            return signBatch(data, key, cert);
        } catch (Exception e) {
            throw new IllegalStateException("EFGS batch signing failed.", e);
        }
    }

    public X509Certificate getTrustAnchor() {
        try {
            return (X509Certificate) keyStore.getCertificate(trustAnchorAlias);
        } catch (KeyStoreException e) {
            throw new RuntimeException(e.getMessage(), e.getCause());
        }
    }

    public X509Certificate getSignerCertificate() throws KeyStoreException {
        return (X509Certificate) keyStore.getCertificate(keyStoreKeyAlias);
    }

    public PrivateKey getTrustAnchorPrivateKey()
            throws SecurityException, KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        return (PrivateKey) keyStore.getKey(trustAnchorAlias, keyStorePassword);
    }

    private KeyStore initKeystore() throws Exception {
        return initDevKeyStore();
    }

    private KeyStore initDevKeyStore() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null);
        KeyPair trustAnchorKeyPair = generateKeyPair();
        X509Certificate trustAnchorCert = generateDevRootCertificate(trustAnchorKeyPair);
        keyStore.setCertificateEntry(trustAnchorAlias, trustAnchorCert);
        keyStore.setKeyEntry(trustAnchorAlias, trustAnchorKeyPair.getPrivate(), keyStorePassword, new java.security.cert.Certificate[]{trustAnchorCert});
        KeyPair keyPair = generateKeyPair();
        X509Certificate certificate = generateDevCertificate(keyPair, trustAnchorKeyPair, trustAnchorCert);
        keyStore.setCertificateEntry(keyStoreKeyAlias, certificate);
        keyStore.setKeyEntry(keyStoreKeyAlias, keyPair.getPrivate(), keyStorePassword, new Certificate[]{certificate});

        return keyStore;
    }

    public KeyPair generateKeyPair() throws NoSuchAlgorithmException, NoSuchProviderException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");
        keyGen.initialize(2048);
        return keyGen.generateKeyPair();
    }

    public X509Certificate generateDevCertificate(KeyPair keyPair, KeyPair trustAnchorKeyPair, X509Certificate trustAnchorCert)
            throws OperatorCreationException, IOException, CertificateException, NoSuchAlgorithmException,
            NoSuchProviderException, InvalidKeyException, SignatureException {

        X500Name subject = new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.C, "FI")
                .addRDN(BCStyle.CN, "koronavilkku-dev")
                .addRDN(BCStyle.O, "koronavilkku dev")
                .build();

        PKCS10CertificationRequestBuilder p10Builder = new JcaPKCS10CertificationRequestBuilder(subject, keyPair.getPublic());
        JcaContentSignerBuilder csrBuilder = new JcaContentSignerBuilder(DIGEST_ALGORITHM + "RSA").setProvider("BC");
        ContentSigner csrContentSigner = csrBuilder.build(trustAnchorKeyPair.getPrivate());
        PKCS10CertificationRequest csr = p10Builder.build(csrContentSigner);
        X509v3CertificateBuilder issuedCertBuilder = new X509v3CertificateBuilder(
                new X500Name("CN=" + DEV_TRUST_ANCHOR_ISSUER),
                new BigInteger(Long.toString(new SecureRandom().nextLong())),
                Date.from(Instant.now()),
                Date.from(Instant.now().plus(Duration.ofDays(100))),
                csr.getSubject(),
                csr.getSubjectPublicKeyInfo()
        );

        JcaX509ExtensionUtils issuedCertExtUtils = new JcaX509ExtensionUtils();
        issuedCertBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        issuedCertBuilder.addExtension(Extension.authorityKeyIdentifier, false, issuedCertExtUtils.createAuthorityKeyIdentifier(trustAnchorCert));
        issuedCertBuilder.addExtension(Extension.subjectKeyIdentifier, false, issuedCertExtUtils.createSubjectKeyIdentifier(csr.getSubjectPublicKeyInfo()));
        issuedCertBuilder.addExtension(Extension.keyUsage, false, new KeyUsage(KeyUsage.digitalSignature));

        X509CertificateHolder issuedCertHolder = issuedCertBuilder.build(csrContentSigner);
        X509Certificate issuedCert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(issuedCertHolder);

        issuedCert.verify(trustAnchorCert.getPublicKey(), "BC");

        return issuedCert;
    }

    public X509Certificate generateDevRootCertificate(KeyPair keyPair) throws OperatorCreationException, IOException, CertificateException, NoSuchAlgorithmException {
        X500Name subject = new X500Name("CN=" + DEV_TRUST_ANCHOR_ISSUER);
        ContentSigner signer = new JcaContentSignerBuilder(DIGEST_ALGORITHM + "RSA").build(keyPair.getPrivate());
        X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
                subject,
                new BigInteger(Long.toString(new SecureRandom().nextLong())),
                Date.from(Instant.now()),
                Date.from(Instant.now().plus(Duration.ofDays(100))),
                subject,
                SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded()));
        JcaX509ExtensionUtils rootCertExtUtils = new JcaX509ExtensionUtils();
        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        certBuilder.addExtension(Extension.subjectKeyIdentifier, false, rootCertExtUtils.createSubjectKeyIdentifier(keyPair.getPublic()));

        X509CertificateHolder rootCertHolder = certBuilder.build(signer);
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(rootCertHolder);
    }
}
