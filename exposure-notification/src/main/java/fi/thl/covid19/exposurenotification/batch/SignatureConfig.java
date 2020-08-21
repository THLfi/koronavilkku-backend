package fi.thl.covid19.exposurenotification.batch;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

import static java.util.Objects.requireNonNull;

@ConstructorBinding
@ConfigurationProperties(prefix = "covid19.diagnosis.signature")
public class SignatureConfig {
    public final String keyVersion;
    public final String keyId;
    public final String algorithmOid;
    public final String algorithmName;

    public SignatureConfig(String keyVersion, String keyId, String algorithmOid, String algorithmName) {
        this.keyVersion = requireNonNull(keyVersion);
        this.keyId = requireNonNull(keyId);
        this.algorithmOid = requireNonNull(algorithmOid);
        this.algorithmName = requireNonNull(algorithmName);
    }
}
