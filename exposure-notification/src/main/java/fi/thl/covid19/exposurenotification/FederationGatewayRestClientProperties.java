package fi.thl.covid19.exposurenotification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConfigurationProperties(prefix = "covid19.federation-gateway.rest-client")
@ConstructorBinding
public class FederationGatewayRestClientProperties {

    private final TrustStore trustStore;
    private final ClientKeyStore clientKeyStore;

    public FederationGatewayRestClientProperties(TrustStore trustStore, ClientKeyStore clientKeyStore) {
        this.trustStore = trustStore;
        this.clientKeyStore = clientKeyStore;
    }

    public boolean isMandatoryPropertiesAvailable() {
        return !this.getTrustStore().path.isBlank() &&
                this.getTrustStore().password.length > 0 &&
                !this.clientKeyStore.getPath().isBlank() &&
                this.clientKeyStore.getPassword().length > 0;
    }

    public static class TrustStore {
        private final String path;
        private final char[] password;

        public TrustStore(String path, char[] password) {
            this.path = path;
            this.password = password;
        }

        public String getPath() {
            return path;
        }

        public char[] getPassword() {
            return password;
        }
    }

    public static class ClientKeyStore {
        private final String path;
        private final char[] password;
        private final String alias;

        public ClientKeyStore(String path, char[] password, String alias) {
            this.path = path;
            this.password = password;
            this.alias = alias;
        }

        public String getPath() {
            return path;
        }

        public char[] getPassword() {
            return password;
        }

        public String getAlias() {
            return alias;
        }
    }

    public TrustStore getTrustStore() {
        return trustStore;
    }

    public ClientKeyStore getClientKeyStore() {
        return clientKeyStore;
    }
}
