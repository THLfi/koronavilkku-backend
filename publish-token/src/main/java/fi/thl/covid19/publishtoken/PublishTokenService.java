package fi.thl.covid19.publishtoken;

import fi.thl.covid19.publishtoken.generation.v1.PublishToken;
import fi.thl.covid19.publishtoken.verification.v1.PublishTokenVerification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.Objects.requireNonNull;

@Service
public class PublishTokenService {

    private static final Logger LOG = LoggerFactory.getLogger(PublishTokenService.class);

    private static final int HALF_TOKEN_RANGE = 1000000;

    private final SecureRandom random = new SecureRandom();

    private final PublishTokenDao dao;
    private final Duration tokenValidityDuration;

    public PublishTokenService(
            PublishTokenDao dao,
            @Value("${covid19.publish-token.validity-duration}") Duration tokenValidityDuration) {
        this.dao = requireNonNull(dao);
        this.tokenValidityDuration = requireNonNull(tokenValidityDuration);
        LOG.info("Initialized: tokenValidityDuration={}", tokenValidityDuration);
    }

    public PublishToken generateAndStore(LocalDate symptomsOnset, String requestService, String requestUser) {
        int retryCount = 0;
        PublishToken token = generate();
        while (!dao.storeToken(token, symptomsOnset, requestService, requestUser)) {
            if (retryCount++ >= 5) throw new IllegalStateException("Unable to generate unique token!");
            token = generate();
        }
        return token;
    }

    public PublishToken generate() {
        Instant now = Instant.now().truncatedTo(SECONDS);
        // Java Random does not generate longs at a desired range, so we generate 12-number token in 2 parts
        String token = String.format("%06d%06d", random.nextInt(HALF_TOKEN_RANGE), random.nextInt(HALF_TOKEN_RANGE));
        return new PublishToken(token, now, now.plus(tokenValidityDuration));
    }

    public List<PublishToken> getTokensBy(String originService, String originUser) {
        return dao.getTokens(originService, originUser);
    }

    public Optional<PublishTokenVerification> getVerification(String token) {
        return dao.getVerification(token);
    }
}
