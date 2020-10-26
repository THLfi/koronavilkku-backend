package fi.thl.covid19.exposurenotification.diagnosiskey.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import fi.thl.covid19.exposurenotification.diagnosiskey.Validation;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static fi.thl.covid19.exposurenotification.diagnosiskey.Validation.*;
import static java.util.Objects.requireNonNull;

/**
 * Posted key data, adjusted from the definition in
 * https://developers.google.com/android/exposure-notifications/verification-system#metadata
 */
public final class TemporaryExposureKeyRequest {
    /** Key of infected user: the byte-array, Base64-encoded **/
    public final String keyData;
    /** Varying risk associated with a key depending on diagnosis method **/
    public final int transmissionRiskLevel;
    /** The interval number since epoch for which a key starts **/
    public final int rollingStartIntervalNumber;
    /** Increments of 10 minutes describing how long a key is valid **/
    public final int rollingPeriod;
    /** List of visited countries in ISO-3166 alpha-2 format **/
    public final Set<String> visitedCountries;

    @JsonCreator
    public TemporaryExposureKeyRequest(String keyData,
                                       int transmissionRiskLevel,
                                       int rollingStartIntervalNumber,
                                       int rollingPeriod,
                                       Optional<Set<String>> visitedCountries) {
        this.keyData = validateKeyData(requireNonNull(keyData));
        this.transmissionRiskLevel = validateTransmissionRiskLevel(transmissionRiskLevel);
        this.rollingStartIntervalNumber = validateRollingStartIntervalNumber(rollingStartIntervalNumber);
        this.rollingPeriod = validateRollingPeriod(rollingPeriod);
        this.visitedCountries = Validation.validateISOCountryCodes(requireNonNull(visitedCountries).orElse(Set.of()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TemporaryExposureKeyRequest that = (TemporaryExposureKeyRequest) o;
        return transmissionRiskLevel == that.transmissionRiskLevel &&
                rollingStartIntervalNumber == that.rollingStartIntervalNumber &&
                rollingPeriod == that.rollingPeriod &&
                keyData.equals(that.keyData) &&
                visitedCountries.equals(that.visitedCountries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyData, transmissionRiskLevel, rollingStartIntervalNumber, rollingPeriod, visitedCountries);
    }

    @Override
    public String toString() {
        return "TemporaryExposureKey{" +
                "keyData='" + keyData + '\'' +
                ", transmissionRiskLevel=" + transmissionRiskLevel +
                ", rollingStartIntervalNumber=" + rollingStartIntervalNumber +
                ", rollingPeriod=" + rollingPeriod +
                ", visitedCountries=" + visitedCountries.toString() + '}';
    }
}
