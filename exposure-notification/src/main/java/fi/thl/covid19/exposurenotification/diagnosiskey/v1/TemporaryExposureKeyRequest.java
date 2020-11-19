package fi.thl.covid19.exposurenotification.diagnosiskey.v1;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import static fi.thl.covid19.exposurenotification.diagnosiskey.Validation.*;
import static java.util.Objects.requireNonNull;

/**
 * Posted key data, adjusted from the definition in
 * https://developers.google.com/android/exposure-notifications/verification-system#metadata
 */
public final class TemporaryExposureKeyRequest {
    /**
     * Key of infected user: the byte-array, Base64-encoded
     **/
    public final String keyData;
    /**
     * Varying risk associated with a key depending on diagnosis method
     **/
    public final int transmissionRiskLevel;
    /**
     * The interval number since epoch for which a key starts
     **/
    public final int rollingStartIntervalNumber;
    /**
     * Increments of 10 minutes describing how long a key is valid
     **/
    public final int rollingPeriod;

    @JsonCreator
    public TemporaryExposureKeyRequest(String keyData,
                                       int transmissionRiskLevel,
                                       int rollingStartIntervalNumber,
                                       int rollingPeriod) {
        this.keyData = validateKeyData(requireNonNull(keyData));
        this.transmissionRiskLevel = validateTransmissionRiskLevel(transmissionRiskLevel);
        this.rollingStartIntervalNumber = validateRollingStartIntervalNumber(rollingStartIntervalNumber);
        this.rollingPeriod = validateRollingPeriod(rollingPeriod);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TemporaryExposureKeyRequest that = (TemporaryExposureKeyRequest) o;
        return transmissionRiskLevel == that.transmissionRiskLevel &&
                rollingStartIntervalNumber == that.rollingStartIntervalNumber &&
                rollingPeriod == that.rollingPeriod &&
                keyData.equals(that.keyData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyData, transmissionRiskLevel, rollingStartIntervalNumber, rollingPeriod);
    }

    @Override
    public String toString() {
        return "TemporaryExposureKey{" +
                "keyData='" + keyData + '\'' +
                ", transmissionRiskLevel=" + transmissionRiskLevel +
                ", rollingStartIntervalNumber=" + rollingStartIntervalNumber +
                ", rollingPeriod=" + rollingPeriod + '}';
    }
}
