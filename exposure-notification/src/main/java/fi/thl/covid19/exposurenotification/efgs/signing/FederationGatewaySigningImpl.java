package fi.thl.covid19.exposurenotification.efgs.signing;

import fi.thl.covid19.proto.EfgsProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.*;

import static fi.thl.covid19.exposurenotification.efgs.util.SigningUtil.signBatch;
import static java.util.Objects.requireNonNull;
import static net.logstash.logback.argument.StructuredArguments.keyValue;


@Component
@ConditionalOnProperty(
        prefix = "covid19.federation-gateway.signing-key-store", value = "implementation",
        havingValue = "default", matchIfMissing = true
)
public class FederationGatewaySigningImpl implements FederationGatewaySigning {

    private static final Logger LOG = LoggerFactory.getLogger(FederationGatewaySigningImpl.class);

    private final String keyStorePath;
    private final char[] keyStorePassword;
    private final String keyStoreKeyAlias;
    private final String trustAnchorAlias;
    private final KeyStore keyStore;

    public FederationGatewaySigningImpl(
            @Value("${covid19.federation-gateway.signing-key-store.path}") String keyStorePath,
            @Value("${covid19.federation-gateway.signing-key-store.password}") String keyStorePassword,
            @Value("${covid19.federation-gateway.signing-key-store.key-alias}") String keyStoreKeyAlias,
            @Value("${covid19.federation-gateway.signing-key-store.trust-anchor-alias}") String trustAnchorAlias
    ) {
        this.keyStorePath = requireNonNull(keyStorePath);
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
            return requireNonNull((X509Certificate) keyStore.getCertificate(trustAnchorAlias));
        } catch (KeyStoreException e) {
            throw new RuntimeException(e.getMessage(), e.getCause());
        }
    }

    private KeyStore initKeystore() {
        return loadKeyStore();
    }

    private KeyStore loadKeyStore() {
        try (FileInputStream fileInputStream = new FileInputStream(ResourceUtils.getFile(keyStorePath))) {
            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
            KeyStore keyStore = KeyStore.getInstance("PKCS12", "BC");
            keyStore.load(fileInputStream, keyStorePassword);
            keyStore.aliases().asIterator().forEachRemaining(alias -> LOG.info("key: {}", keyValue("alias", alias)));
            return keyStore;
        } catch (KeyStoreException | NoSuchAlgorithmException | IOException | CertificateException | NoSuchProviderException e) {
            throw new IllegalStateException("EFGS signing certificate load error.", e);
        }
    }
}
