package fi.thl.covid19.exposurenotification.efgs;

import fi.thl.covid19.proto.EfgsProto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static fi.thl.covid19.exposurenotification.efgs.FederationGatewayBatchUtil.deserialize;
import static fi.thl.covid19.exposurenotification.efgs.FederationGatewayBatchUtil.serialize;
import static java.util.Objects.requireNonNull;

@Component
public class FederationGatewayClient {

    public static final String BATCH_TAG_HEADER = "batchTag";
    public static final String NEXT_BATCH_TAG_HEADER = "nextBatchTag";

    private final RestTemplate restTemplate;
    private final String gatewayUrl;
    private final Environment environment;
    private final String devSha256;
    private final String devDN;

    public FederationGatewayClient(
            @Qualifier("federationGatewayRestTemplate") RestTemplate restTemplate,
            @Value("${covid19.federation-gateway.url-template}") String gatewayUrl,
            Environment environment,
            @Value("${covid19.federation-gateway.client-sha256:not_set}") String devSha256,
            @Value("${covid19.federation-gateway.client-dn:not_set}") String devDN
    ) {
        this.restTemplate = restTemplate;
        this.gatewayUrl = gatewayUrl;
        this.environment = environment;
        this.devSha256 = devSha256;
        this.devDN = devDN;
    }

    public UploadResponseEntity upload(String batchTag, String batchSignature, EfgsProto.DiagnosisKeyBatch batchData) {
        return transform(restTemplate.exchange(
                gatewayUrl,
                HttpMethod.POST,
                new HttpEntity<>(serialize(batchData), getUploadHttpHeaders(batchTag, batchSignature)),
                UploadResponseEntityInner.class,
                getUriVariables("upload", "")
        ));
    }

    public List<EfgsProto.DiagnosisKeyBatch> download(String dateVar, Optional<String> batchTag) {
        Optional<String> nextTag = batchTag;
        List<EfgsProto.DiagnosisKeyBatch> data = new ArrayList<>();

        do {
            ResponseEntity<byte[]> res = doDownload(dateVar, nextTag);

            if (res.hasBody()) {
                data.add(deserialize(res.getBody()));
            }

            nextTag = getNextBatchTag(res.getHeaders());
        } while (nextTag.isPresent());

        return data;
    }

    private UploadResponseEntity transform(ResponseEntity<UploadResponseEntityInner> res) {
        Optional<Map<Integer, List<Integer>>> body =
                res.getStatusCodeValue() == 207 ?
                        Optional.of(Map.of(
                                201, requireNonNull(requireNonNull(res.getBody()).get(201)),
                                409, requireNonNull(requireNonNull(res.getBody()).get(409)),
                                500, requireNonNull(requireNonNull(res.getBody()).get(500))
                        )) : Optional.empty();

        return new UploadResponseEntity(res.getStatusCode(), body);
    }

    private ResponseEntity<byte[]> doDownload(String dateVar, Optional<String> batchTag) {
        return restTemplate.exchange(
                gatewayUrl,
                HttpMethod.GET,
                new HttpEntity<>(getDownloadHttpHeaders(batchTag)),
                byte[].class,
                getUriVariables("download", dateVar)
        );
    }

    private HttpHeaders getUploadHttpHeaders(String batchTag, String batchSignature) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("batchTag", batchTag);
        headers.add("batchSignature", batchSignature);
        headers.add(HttpHeaders.CONTENT_TYPE, "application/protobuf; version=1.0");

        if (Arrays.stream(environment.getActiveProfiles()).anyMatch("dev"::equalsIgnoreCase)) {
            addDevHeaders(headers);
        }

        return headers;
    }

    private HttpHeaders getDownloadHttpHeaders(Optional<String> batchTag) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, "application/protobuf; version=1.0");
        batchTag.ifPresent(s -> headers.add(BATCH_TAG_HEADER, s));

        if (Arrays.stream(environment.getActiveProfiles()).anyMatch("dev"::equalsIgnoreCase)) {
            addDevHeaders(headers);
        }

        return headers;
    }

    private Map<String, String> getUriVariables(String operation, String uriDate) {
        return Map.of(
                "operation", operation,
                "variable", uriDate
        );
    }

    private void addDevHeaders(HttpHeaders headers) {
        headers.add("X-SSL-Client-SHA256", devSha256);
        headers.add("X-SSL-Client-DN", devDN);
    }

    private Optional<String> getNextBatchTag(HttpHeaders headers) {
        List<String> nextBatchTagHeader = headers.get(NEXT_BATCH_TAG_HEADER);
        if (nextBatchTagHeader != null && !nextBatchTagHeader.contains("null")) {
            return nextBatchTagHeader.stream().findFirst();
        } else {
            return Optional.empty();
        }
    }

    public static class UploadResponseEntityInner extends HashMap<Integer, List<Integer>> {
    }
}
