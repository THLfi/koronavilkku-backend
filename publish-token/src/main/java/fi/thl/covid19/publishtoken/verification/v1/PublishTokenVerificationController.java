package fi.thl.covid19.publishtoken.verification.v1;

import fi.thl.covid19.publishtoken.PublishTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import static fi.thl.covid19.publishtoken.Validation.validatePublishToken;
import static java.util.Objects.requireNonNull;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

@RestController
@RequestMapping("/verification/v1")
public class PublishTokenVerificationController {

    private static final Logger LOG = LoggerFactory.getLogger(PublishTokenVerificationController.class);

    public static final String TOKEN_HEADER = "KV-Publish-Token";

    private final PublishTokenService publishTokenService;

    public PublishTokenVerificationController(PublishTokenService publishTokenService) {
        this.publishTokenService = requireNonNull(publishTokenService);
    }

    @GetMapping
    public ResponseEntity<PublishTokenVerification> getVerification(@RequestHeader(TOKEN_HEADER) String token) {
        String validated = validatePublishToken(requireNonNull(token));
        Optional<PublishTokenVerification> result = publishTokenService.getVerification(validated);
        LOG.info("Verifying token: {}", keyValue("accepted", result.isPresent()));
        return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
}
