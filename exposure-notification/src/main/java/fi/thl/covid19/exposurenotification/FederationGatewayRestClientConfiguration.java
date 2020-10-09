package fi.thl.covid19.exposurenotification;

import fi.thl.covid19.exposurenotification.error.RestTemplateErrorHandler;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.PrivateKeyStrategy;
import org.apache.http.ssl.SSLContextBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.ResourceUtils;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.time.Duration;

@Configuration
public class FederationGatewayRestClientConfiguration {

    private final Duration connectTimeout = Duration.ofSeconds(60);
    private final Duration readTimeout = Duration.ofSeconds(60);
    private final Duration socketTimeout = Duration.ofSeconds(60);

    private final String trustStorePath;
    private final char[] trustStorePassword;
    private final String keyStorePath;
    private final char[] keyStorePassword;
    private final String keyStoreKeyAlias;

    public FederationGatewayRestClientConfiguration(
            @Value("${covid19.federation-gateway.trust-store.path}") String trustStorePath,
            @Value("${covid19.federation-gateway.trust-store.password}") String trustStorePassword,
            @Value("${covid19.federation-gateway.key-store.path}") String keyStorePath,
            @Value("${covid19.federation-gateway.key-store.password}") String keyStorePassword,
            @Value("${covid19.federation-gateway.key-store.alias}") String keyStoreKeyAlias
    ) {
        this.trustStorePath = trustStorePath;
        this.trustStorePassword = trustStorePassword.toCharArray();
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword.toCharArray();
        this.keyStoreKeyAlias = keyStoreKeyAlias;
    }

    @Bean("federationGatewayRestTemplate")
    public RestTemplate customRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(connectTimeout)
                .setReadTimeout(readTimeout)
                .setConnectTimeout(socketTimeout)
                .requestFactory(this::requestFactory)
                .errorHandler(new RestTemplateErrorHandler())
                .build();
    }

    private HttpComponentsClientHttpRequestFactory requestFactory() {
        try {
            SSLContext context = (!trustStorePath.isBlank() &&
                    trustStorePassword.length > 0 &&
                    !keyStorePath.isBlank() &&
                    keyStorePassword.length > 0) ? createSSLContextWithKey() : SSLContextBuilder.create().build();

            SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(context);
            CloseableHttpClient client = HttpClients.custom()
                    .setSSLSocketFactory(socketFactory)
                    .build();
            return new HttpComponentsClientHttpRequestFactory(client);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private SSLContext createSSLContextWithKey() throws Exception {
        PrivateKeyStrategy privateKeyStrategy = (v1, v2) -> keyStoreKeyAlias;
        return SSLContextBuilder.create()
                .loadKeyMaterial(keyStore(keyStorePath, keyStorePassword), keyStorePassword, privateKeyStrategy)
                .loadTrustMaterial(new File(trustStorePath), trustStorePassword)
                .build();
    }

    private KeyStore keyStore(String keyStoreFile, char[] password) throws Exception {
        File file = ResourceUtils.getFile(keyStoreFile);
        return loadKeyStore(file, password);
    }

    private KeyStore loadKeyStore(File file, char[] password) throws Exception {
        KeyStore keyStore;

        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(fileInputStream, password);
        }

        return keyStore;
    }
}
