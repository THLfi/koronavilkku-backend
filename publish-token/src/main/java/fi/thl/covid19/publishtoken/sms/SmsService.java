package fi.thl.covid19.publishtoken.sms;

import fi.thl.covid19.publishtoken.generation.v1.PublishToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Random;

import static java.util.Objects.requireNonNull;

@Service
public class SmsService {

    private static final Logger LOG = LoggerFactory.getLogger(SmsService.class);

    private static final Random RAND = new Random();

    private final RestTemplate restTemplate;

    private final SmsConfig config;

    public SmsService(RestTemplate restTemplate, SmsConfig config) {
        this.restTemplate = requireNonNull(restTemplate);
        this.config = requireNonNull(config);
        LOG.info("SMS Service initialized: active={} senderName={} gateway={}",
                config.gateway.isPresent(), config.senderName, config.gateway.orElse(""));
    }

    public boolean send(String number, PublishToken token) {
        if (config.gateway.isPresent()) {
            return send(config.gateway.get(), number, config.formatContent(token.token));
        } else {
            LOG.warn("Requested to send SMS, but no gateway configured!");
            return false;
        }
    }

    private boolean send(String gateway, String number, String content) {
        String sendEventId = Integer.toString(RAND.nextInt(Integer.MAX_VALUE));

        LOG.info("Sending token via SMS: gateway={} length={} eventId={}", gateway, content.length(), sendEventId);

        try {
            HttpEntity<MultiValueMap<String, String>> request = formRequest(sendEventId, number, content);
            ResponseEntity<String> result = restTemplate.postForEntity(gateway, request, String.class);
            if (result.getStatusCode().is2xxSuccessful()) {
                LOG.info("SMS sent: eventId={} status={}", sendEventId, result.getStatusCode());
                return true;
            } else {
                LOG.error("Failed to send SMS: eventId={} status={}", sendEventId, result.getStatusCode());
                return false;
            }
        } catch (RestClientException e) {
            LOG.error("Failed to send SMS: eventId={}", sendEventId, e);
            return false;
        }
    }

    private HttpEntity<MultiValueMap<String, String>> formRequest(String sendEventId, String number, String content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.TEXT_PLAIN));

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("sender", config.senderName);
        params.add("senderId", config.senderId);
        params.add("orgOid", config.orgId);
        params.add("eventOid", sendEventId);
        params.add("recipient", number);
        params.add("text", content);

        return new HttpEntity<>(params, headers);
    }
}
