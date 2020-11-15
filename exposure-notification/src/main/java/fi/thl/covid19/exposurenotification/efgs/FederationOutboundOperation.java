package fi.thl.covid19.exposurenotification.efgs;

import fi.thl.covid19.exposurenotification.diagnosiskey.TemporaryExposureKey;

import java.util.List;

public class FederationOutboundOperation {
    public final List<TemporaryExposureKey> keys;
    public final long operationId;

    public FederationOutboundOperation(List<TemporaryExposureKey> keys, long operationId) {
        this.keys = keys;
        this.operationId = operationId;
    }
}
