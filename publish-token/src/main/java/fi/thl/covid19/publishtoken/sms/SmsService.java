package fi.thl.covid19.publishtoken.sms;

import fi.thl.covid19.publishtoken.PublishTokenDao;
import fi.thl.covid19.publishtoken.generation.v1.PublishToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

@Service
public class SmsService {

    private static final Logger LOG = LoggerFactory.getLogger(SmsService.class);

    private final RestTemplate restTemplate;

    private final SmsConfig config;

    private final PublishTokenDao dao;

    public SmsService(RestTemplate restTemplate, SmsConfig config, PublishTokenDao dao) {
        this.restTemplate = requireNonNull(restTemplate);
        this.config = requireNonNull(config);
        this.dao = requireNonNull(dao);
        LOG.info("SMS Service initialized: {} {} {}",
                keyValue("active", config.gateway.isPresent()),
                keyValue("senderName", config.senderName),
                keyValue("gateway", config.gateway.orElse("")));
    }

    public boolean send(String number, PublishToken token) {
        if (config.gateway.isPresent() && send(config.gateway.get(), number, config.formatContent(token.token))) {
            dao.addSmsStatsRow(Instant.now());
            return true;
        } else {
            LOG.warn("Requested to send SMS, but no gateway configured!");
            return false;
        }
    }

    private boolean send(String gateway, String number, String content) {

        LOG.info("Sending token via SMS: {} {}",
                keyValue("gateway", gateway),
                keyValue("length", content.length()));

        try {
            HttpEntity<SmsPayload> request = generateRequest(number, content);
            ResponseEntity<String> result = restTemplate.postForEntity(gateway, request, String.class);
            if (result.getStatusCode().is2xxSuccessful()) {
                LOG.info("SMS sent: {}", keyValue("status", result.getStatusCode()));
                return true;
            } else {
                LOG.error("Failed to send SMS: {}", keyValue("status", result.getStatusCode()));
                return false;
            }
        } catch (RestClientException e) {
            LOG.error("Failed to send SMS.", e);
            return false;
        }
    }

    private HttpEntity<SmsPayload> generateRequest(String number, String content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.add("Authorization", "apikey " + config.senderApiKey);
        SmsPayload sms = new SmsPayload(config.senderName, content, Set.of(number));

        return new HttpEntity<>(sms, headers);
    }
}
