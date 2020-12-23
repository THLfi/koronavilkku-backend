package fi.thl.covid19.publishtoken.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.util.StringUtils;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

@ConstructorBinding
@ConfigurationProperties(prefix = "covid19.publish-token.sms")
public class SmsConfig {

    private static final int MAX_SMS_LENGTH = 500;
    private static final String SMS_CODE_PART = "<code>";
    private static final int TOKEN_LENGTH = 12;

    public final Optional<String> gateway;
    public final String senderName;
    public final String content;
    public final String senderId;

    public SmsConfig(Optional<String> gateway, String senderName, String senderId, String content) {
        this.gateway = requireNonNull(gateway).map(String::trim).filter(s -> !s.isEmpty());
        this.senderName = requireNonNull(senderName);
        this.content = requireNonNull(content);
        this.senderId = requireNonNull(senderId);
        if (content.trim().isEmpty()) {
            throw new IllegalStateException("Invalid SMS config: empty content");
        }
        if (calculateContentLengthAfterTokenReplace(content) > MAX_SMS_LENGTH) {
            throw new IllegalStateException("Invalid SMS config: content length " + calculateContentLengthAfterTokenReplace(content) + ">" + MAX_SMS_LENGTH);
        }
        if (!content.contains(SMS_CODE_PART)) {
            throw new IllegalStateException("Invalid SMS config: content doesn't contain " + SMS_CODE_PART + " segment for the token.");
        }
    }

    private int calculateContentLengthAfterTokenReplace(String content) {
        return (content.length() + StringUtils.countOccurrencesOf(content, SMS_CODE_PART) * (TOKEN_LENGTH - SMS_CODE_PART.length()));
    }

    public String formatContent(String token) {
        return content.replace(SMS_CODE_PART, token);
    }
}
