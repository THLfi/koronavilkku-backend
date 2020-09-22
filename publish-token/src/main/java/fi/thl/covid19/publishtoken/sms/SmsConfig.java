package fi.thl.covid19.publishtoken.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

@ConstructorBinding
@ConfigurationProperties(prefix = "covid19.publish-token.sms")
public class SmsConfig {

    private static final int MAX_SMS_LENGTH = 500;
    private static final String SMS_CODE_PART = "<code>";

    public final Optional<String> gateway;
    public final String senderName;
    public final String content;
    public final String senderId;
    public final String orgId;

    public SmsConfig(Optional<String> gateway, String senderName, String senderId, String orgId, String content) {
        this.gateway = requireNonNull(gateway).map(String::trim).filter(s -> !s.isEmpty());
        this.senderName = requireNonNull(senderName);
        this.content = requireNonNull(content);
        this.senderId = requireNonNull(senderId);
        this.orgId = requireNonNull(orgId);
        if (content.trim().isEmpty()) {
            throw new IllegalStateException("Invalid SMS config: empty content");
        }
        if (content.length() > MAX_SMS_LENGTH) {
            throw new IllegalStateException("Invalid SMS config: content length " + content.length() + ">" + MAX_SMS_LENGTH);
        }
        if (!content.contains(SMS_CODE_PART)) {
            throw new IllegalStateException("Invalid SMS config: content doesn't contain " + SMS_CODE_PART + " segment for the token.");
        }
    }

    public String formatContent(String token) {
        return content.replace(SMS_CODE_PART, token);
    }
}
