package fi.thl.covid19.exposurenotification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

import static java.util.Objects.requireNonNull;

@ConfigurationProperties(prefix = "covid19.federation-gateway.rest-client")
@ConstructorBinding
public class FederationGatewayRestClientProperties {

    public final TrustStore trustStore;
    public final ClientKeyStore clientKeyStore;

    public FederationGatewayRestClientProperties(TrustStore trustStore, ClientKeyStore clientKeyStore) {
        this.trustStore = requireNonNull(trustStore);
        this.clientKeyStore = requireNonNull(clientKeyStore);
    }

    public boolean isMandatoryPropertiesAvailable() {
        return !this.trustStore.path.isBlank() &&
                this.trustStore.password.length > 0 &&
                !this.clientKeyStore.path.isBlank() &&
                this.clientKeyStore.password.length > 0;
    }

    public static class TrustStore {
        public final String path;
        public final char[] password;

        public TrustStore(String path, char[] password) {
            this.path = requireNonNull(path);
            this.password = requireNonNull(password);
        }
    }

    public static class ClientKeyStore {
        public final String path;
        public final char[] password;
        public final String alias;

        public ClientKeyStore(String path, char[] password, String alias) {
            this.path = requireNonNull(path);
            this.password = requireNonNull(password);
            this.alias = requireNonNull(alias);
        }
    }
}
