package fi.thl.covid19.exposurenotification.efgs;

import fi.thl.covid19.exposurenotification.diagnosiskey.TemporaryExposureKey;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static fi.thl.covid19.exposurenotification.efgs.FederationGatewayBatchUtil.getBatchTag;

public class FederationOutboundOperation {
    public final List<TemporaryExposureKey> keys;
    public final long operationId;
    public final String batchTag;

    public FederationOutboundOperation(List<TemporaryExposureKey> keys, long operationId) {
        this.keys = keys;
        this.operationId = operationId;
        this.batchTag = getBatchTag(LocalDate.now(ZoneOffset.UTC), Long.toString(operationId));
    }
}
