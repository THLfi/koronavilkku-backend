package fi.thl.covid19.exposurenotification.diagnosiskey.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import fi.thl.covid19.exposurenotification.diagnosiskey.Validation;
import fi.thl.covid19.exposurenotification.error.InputValidationException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class DiagnosisPublishRequest {

    private static final int KEYS_PER_REQUEST = 14;

    public final List<TemporaryExposureKeyRequest> keys;
    /**
     * Set of visited countries in ISO-3166 alpha-2 format
     **/
    public final Map<String, Boolean> visitedCountries;

    /**
     * Set of visited countries in ISO-3166 alpha-2 format
     **/
    public final Set<String> visitedCountriesSet;

    /**
     * Consent to share data with efgs
     **/
    public final boolean consentToShareWithEfgs;

    @JsonCreator
    public DiagnosisPublishRequest(
            List<TemporaryExposureKeyRequest> keys,
            Optional<Map<String, Boolean>> visitedCountries,
            Optional<Boolean> consentToShareWithEfgs
    ) {
        if (keys.size() != KEYS_PER_REQUEST) {
            throw new InputValidationException("The request should contain exactly " + KEYS_PER_REQUEST + " keys");
        }
        this.keys = requireNonNull(keys);
        this.visitedCountries = requireNonNull(visitedCountries).orElse(Map.of());
        this.visitedCountriesSet = Validation.validateISOCountryCodesWithoutFI(this.visitedCountries);
        this.consentToShareWithEfgs = requireNonNull(consentToShareWithEfgs).orElse(false);
    }
}
