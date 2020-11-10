package fi.thl.covid19.exposurenotification.diagnosiskey;


import java.util.Objects;
import java.util.Set;

import static fi.thl.covid19.exposurenotification.diagnosiskey.Validation.*;
import static java.util.Objects.requireNonNull;

public final class TemporaryExposureKey {
    /** Key of infected user: the byte-array, Base64-encoded **/
    public final String keyData;
    /** Varying risk associated with a key depending on diagnosis method **/
    public final int transmissionRiskLevel;
    /** The interval number since epoch for which a key starts **/
    public final int rollingStartIntervalNumber;
    /** Increments of 10 minutes describing how long a key is valid **/
    public final int rollingPeriod;
    /** List of visited countries in ISO-3166 alpha-2 format  **/
    public final Set<String> visitedCountries;
    /** Days since onset of symptoms **/
    public final int daysSinceOnsetOfSymptoms;
    /** Origin country in ISO-3166 alpha-2 format **/
    public final String origin;
    /** Consent to share data with efgs **/
    public final boolean consentToShareWithEfgs;


    public TemporaryExposureKey(String keyData,
                                int transmissionRiskLevel,
                                int rollingStartIntervalNumber,
                                int rollingPeriod,
                                Set<String> visitedCountries,
                                int daysSinceOnsetOfSymptoms,
                                String origin,
                                boolean consentToShareWithEfgs) {
        this.keyData = validateKeyData(requireNonNull(keyData));
        this.transmissionRiskLevel = validateTransmissionRiskLevel(transmissionRiskLevel);
        this.rollingStartIntervalNumber = validateRollingStartIntervalNumber(rollingStartIntervalNumber);
        this.rollingPeriod = validateRollingPeriod(rollingPeriod);
        this.visitedCountries = Validation.validateISOCountryCodes(requireNonNull(visitedCountries));
        this.daysSinceOnsetOfSymptoms = Validation.validateDaysSinceOnsetOfSymptoms(daysSinceOnsetOfSymptoms);
        this.origin = Validation.getValidatedISOCountryCode(requireNonNull(origin));
        this.consentToShareWithEfgs = consentToShareWithEfgs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TemporaryExposureKey that = (TemporaryExposureKey) o;
        return transmissionRiskLevel == that.transmissionRiskLevel &&
                rollingStartIntervalNumber == that.rollingStartIntervalNumber &&
                rollingPeriod == that.rollingPeriod &&
                keyData.equals(that.keyData) &&
                visitedCountries.equals(that.visitedCountries) &&
                daysSinceOnsetOfSymptoms == that.daysSinceOnsetOfSymptoms &&
                origin.equals(that.origin) &&
                consentToShareWithEfgs == that.consentToShareWithEfgs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyData, transmissionRiskLevel, rollingStartIntervalNumber, rollingPeriod,
                visitedCountries, daysSinceOnsetOfSymptoms, origin, consentToShareWithEfgs);
    }

    @Override
    public String toString() {
        return "TemporaryExposureKey{" +
                "keyData='" + keyData + '\'' +
                ", transmissionRiskLevel=" + transmissionRiskLevel +
                ", rollingStartIntervalNumber=" + rollingStartIntervalNumber +
                ", rollingPeriod=" + rollingPeriod +
                ", visitedCountries=" + visitedCountries.toString() +
                ", daysSinceOnsetOfSymptoms=" + daysSinceOnsetOfSymptoms +
                ", origin=" + origin +
                ", consentToShareWithEfgs=" + consentToShareWithEfgs +
                '}';
    }
}
