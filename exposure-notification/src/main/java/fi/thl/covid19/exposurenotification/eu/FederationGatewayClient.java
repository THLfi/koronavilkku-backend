package fi.thl.covid19.exposurenotification.eu;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

@Component
public class FederationGatewayClient {

    private final RestTemplate restTemplate;
    private final String gatewayUrl;

    public FederationGatewayClient(
            @Qualifier("federationGatewayRestTemplate") RestTemplate restTemplate,
            @Value("${covid19.federation-gateway.url-template}") String gatewayUrl
    ) {
        this.restTemplate = restTemplate;
        this.gatewayUrl = gatewayUrl;
    }

    public int upload(String batchTag, String batchSignature, byte[] batchData) {
        return restTemplate.exchange(
                gatewayUrl,
                HttpMethod.POST,
                new HttpEntity<>(batchData, getUploadHttpHeaders(batchTag, batchSignature)),
                String.class,
                getUriVariables("upload", "")
        ).getStatusCodeValue();
    }

    public byte[] download(String dateVar, Optional<String> batchTag) {
        return restTemplate.exchange(
                gatewayUrl,
                HttpMethod.GET,
                new HttpEntity<>(getDownloadHttpHeaders(batchTag)),
                byte[].class,
                getUriVariables("download", dateVar)
        ).getBody();
    }

    private HttpHeaders getUploadHttpHeaders(String batchTag, String batchSignature) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("batchTag", batchTag);
        headers.add("batchSignature", batchSignature);
        headers.add(HttpHeaders.CONTENT_TYPE, "application/protobuf");

        return headers;
    }

    private HttpHeaders getDownloadHttpHeaders(Optional<String> batchTag) {
        HttpHeaders headers = new HttpHeaders();
        batchTag.ifPresent(s -> headers.add("batchTag", s));

        return headers;
    }

    private Map<String, String> getUriVariables(String operation, String uriDate) {
        return Map.of(
                "operation", operation,
                "variable", uriDate
        );
    }
}
