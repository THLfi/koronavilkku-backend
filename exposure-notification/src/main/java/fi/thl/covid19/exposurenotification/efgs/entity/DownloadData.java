package fi.thl.covid19.exposurenotification.efgs.entity;

import fi.thl.covid19.proto.EfgsProto;

import java.util.Optional;


public class DownloadData {
    public final Optional<EfgsProto.DiagnosisKeyBatch> batch;
    public final String batchTag;
    public final Optional<String> nextBatchTag;

    public DownloadData(Optional<EfgsProto.DiagnosisKeyBatch> batch, String batchTag, Optional<String> nextBatchTag) {
        this.batch = batch;
        this.batchTag = batchTag;
        this.nextBatchTag = nextBatchTag;
    }

    public int keysCount() {
        return batch.map(EfgsProto.DiagnosisKeyBatch::getKeysCount).orElse(0);
    }
}
