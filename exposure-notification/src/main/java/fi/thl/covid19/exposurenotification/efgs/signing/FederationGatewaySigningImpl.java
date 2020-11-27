package fi.thl.covid19.exposurenotification.efgs.signing;

import fi.thl.covid19.proto.EfgsProto;
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


@Component
@ConditionalOnProperty(
        prefix = "covid19.federation-gateway.signing-key-store", value = "implementation",
        havingValue = "default", matchIfMissing = true
)
public class FederationGatewaySigningImpl implements FederationGatewaySigning {

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
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(fileInputStream, keyStorePassword);
            return keyStore;
        } catch (KeyStoreException | NoSuchAlgorithmException | IOException | CertificateException e) {
            throw new IllegalStateException("EFGS signing certificate load error.", e);
        }
    }
}
