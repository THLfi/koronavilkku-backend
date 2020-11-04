package fi.thl.covid19.exposurenotification.diagnosiskey.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import fi.thl.covid19.exposurenotification.error.InputValidationException;

import java.util.List;

import static java.util.Objects.requireNonNull;

public class DiagnosisPublishRequest {

    private static final int KEYS_PER_REQUEST = 14;

    public final List<TemporaryExposureKeyRequest> keys;

    @JsonCreator
    public DiagnosisPublishRequest(List<TemporaryExposureKeyRequest> keys) {
        if (keys.size() != KEYS_PER_REQUEST) {
            throw new InputValidationException("The request should contain exactly " + KEYS_PER_REQUEST + " keys");
        }
        this.keys = requireNonNull(keys);
    }
}
