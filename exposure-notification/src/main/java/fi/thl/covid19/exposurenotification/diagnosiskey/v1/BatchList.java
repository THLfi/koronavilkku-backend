package fi.thl.covid19.exposurenotification.diagnosiskey.v1;

import fi.thl.covid19.exposurenotification.batch.BatchId;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class BatchList {
    public final List<String> batches;

    public BatchList(List<BatchId> batches) {
        this.batches = requireNonNull(batches).stream()
                .map(BatchId::toString)
                .collect(Collectors.toList());
    }
}
