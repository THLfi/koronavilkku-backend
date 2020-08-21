package fi.thl.covid19.exposurenotification.tokenverification;

import fi.thl.covid19.exposurenotification.error.TokenValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static java.util.Objects.requireNonNull;

@Service
public class PublishTokenVerificationServiceRest implements PublishTokenVerificationService {

    private static final Logger LOG = LoggerFactory.getLogger(PublishTokenVerificationServiceRest.class);

    public static final String TOKEN_VERIFICATION_PATH = "/verification/v1";
    public static final String PUBLISH_TOKEN_HEADER = "KV-Publish-Token";

    private final RestTemplate restTemplate;
    private final String publishTokenUrl;

    public PublishTokenVerificationServiceRest(
            RestTemplate restTemplate,
            @Value("${covid19.publish-token.url}") String publishTokenUrl) {
        this.restTemplate = requireNonNull(restTemplate, "RestTemplate required");
        this.publishTokenUrl = requireNonNull(publishTokenUrl, "Publish Token URL required");
    }

    @Override
    public PublishTokenVerification getVerification(String token) {
        String url = publishTokenUrl + TOKEN_VERIFICATION_PATH;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.add(PUBLISH_TOKEN_HEADER, token);
            HttpEntity<String> entity = new HttpEntity<>(null, headers);
            ResponseEntity<PublishTokenVerification> reply = restTemplate.exchange(url, HttpMethod.GET, entity, PublishTokenVerification.class);
            if (reply.getStatusCode().is2xxSuccessful() && reply.hasBody()) {
                return reply.getBody();
            } else {
                LOG.warn("Server didn't verify publish token: status={}", reply.getStatusCode());
                throw new TokenValidationException();
            }
        } catch (HttpClientErrorException e) {
            LOG.warn("Publish token not verified: result={}", e.getStatusCode(), e);
            throw new TokenValidationException();
        } catch (HttpServerErrorException e) {
            LOG.error("Error in token service: url={}", url, e);
        } catch (RestClientException e) {
            LOG.error("Error verifying token request: url={}", url, e);
        }
        throw new IllegalStateException("Could not handle token verification");
    }
}
