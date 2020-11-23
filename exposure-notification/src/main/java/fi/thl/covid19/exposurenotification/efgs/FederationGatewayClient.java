package fi.thl.covid19.exposurenotification.efgs;

import fi.thl.covid19.proto.EfgsProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static fi.thl.covid19.exposurenotification.efgs.FederationGatewayBatchUtil.deserialize;
import static fi.thl.covid19.exposurenotification.efgs.FederationGatewayBatchUtil.serialize;
import static java.util.Objects.requireNonNull;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

@Component
public class FederationGatewayClient {

    public static final String BATCH_TAG_HEADER = "batchTag";
    public static final String NEXT_BATCH_TAG_HEADER = "nextBatchTag";

    private static final Logger LOG = LoggerFactory.getLogger(FederationGatewayClient.class);

    private final RestTemplate restTemplate;
    private final String gatewayBaseUrl;
    private final boolean devClient;
    private final String devSha256;
    private final String devDN;

    public FederationGatewayClient(
            @Qualifier("federationGatewayRestTemplate") RestTemplate restTemplate,
            @Value("${covid19.federation-gateway.base-url}") String gatewayBaseUrl,
            @Value("${covid19.federation-gateway.dev-client:false}") boolean devClient,
            @Value("${covid19.federation-gateway.client-sha256:not_set}") String devSha256,
            @Value("${covid19.federation-gateway.client-dn:not_set}") String devDN
    ) {
        this.restTemplate = requireNonNull(restTemplate);
        this.gatewayBaseUrl = requireNonNull(gatewayBaseUrl);
        this.devClient = devClient;
        this.devSha256 = requireNonNull(devSha256);
        this.devDN = requireNonNull(devDN);
    }

    public UploadResponseEntity upload(String batchTag, String batchSignature, EfgsProto.DiagnosisKeyBatch batchData) {
        return transform(restTemplate.exchange(
                gatewayBaseUrl + "/{operation}/{variable}",
                HttpMethod.POST,
                new HttpEntity<>(serialize(batchData), getUploadHttpHeaders(batchTag, batchSignature)),
                UploadResponseEntityInner.class,
                getUriVariables("upload", "")
        ));
    }

    public Optional<DownloadData> download(String dateVar, Optional<String> batchTag) {
        try {
            ResponseEntity<byte[]> res = doDownload(dateVar, batchTag);
            byte[] body = res.getBody();
            return Optional.of(new DownloadData(
                    body == null ? Optional.empty() : Optional.of(deserialize(body)),
                    getHeader(res.getHeaders(), BATCH_TAG_HEADER).orElseThrow(),
                    getHeader(res.getHeaders(), NEXT_BATCH_TAG_HEADER)
            ));
        } catch (HttpClientErrorException e) {
            if (e.getRawStatusCode() == 404) {
                LOG.info("Download from efgs failed with response code 404. {}", keyValue("statusText", e.getStatusText()));
                return Optional.empty();
            } else {
                throw e;
            }
        }
    }

    public List<Callback> fetchCallbacks() {
        ResponseEntity<Callback[]> response = restTemplate.getForEntity(
                gatewayBaseUrl + "/callback",
                Callback[].class
        );
        return Arrays.asList(requireNonNull(response.getBody()));
    }

    public void deleteCallback(String id) {
        restTemplate.delete(
                gatewayBaseUrl + "/callback/{id}",
                Map.of("id", id)
        );
    }

    public void putCallback(Callback callback) {
        restTemplate.put(
                gatewayBaseUrl + "/callback/{id}?url={url}",
                null,
                Map.of(
                        "id", callback.callbackId,
                        "url", callback.url
                )
        );
    }

    public List<AuditEntry> fetchAuditEntries(String dateS, String batchTag) {
        ResponseEntity<AuditEntry[]> response = restTemplate.getForEntity(
                gatewayBaseUrl + "/audit/download/{date}/{batchTag}",
                AuditEntry[].class,
                Map.of("date", dateS, "batchTag", batchTag)
        );
        return Arrays.asList(requireNonNull(response.getBody()));
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
                gatewayBaseUrl + "/{operation}/{variable}",
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

        if (devClient) {
            addDevHeaders(headers);
        }

        return headers;
    }

    private HttpHeaders getDownloadHttpHeaders(Optional<String> batchTag) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, "application/protobuf; version=1.0");
        batchTag.ifPresent(s -> headers.add(BATCH_TAG_HEADER, s));

        if (devClient) {
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

    private Optional<String> getHeader(HttpHeaders headers, String name) {
        List<String> nextBatchTagHeader = headers.get(name);
        if (nextBatchTagHeader != null && !nextBatchTagHeader.contains("null")) {
            return nextBatchTagHeader.stream().findFirst();
        } else {
            return Optional.empty();
        }
    }

    public static class UploadResponseEntityInner extends HashMap<Integer, List<Integer>> {
    }
}
