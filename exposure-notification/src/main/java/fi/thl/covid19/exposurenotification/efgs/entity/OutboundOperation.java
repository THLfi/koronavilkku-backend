package fi.thl.covid19.exposurenotification.efgs.entity;

import fi.thl.covid19.exposurenotification.diagnosiskey.TemporaryExposureKey;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static fi.thl.covid19.exposurenotification.efgs.util.BatchUtil.getBatchTag;

public class OutboundOperation {
    public final List<TemporaryExposureKey> keys;
    public final long operationId;
    public final String batchTag;

    public OutboundOperation(List<TemporaryExposureKey> keys, long operationId) {
        this.keys = keys;
        this.operationId = operationId;
        this.batchTag = "FI__" + getBatchTag(LocalDate.now(ZoneOffset.UTC), Long.toString(operationId));
    }
}
