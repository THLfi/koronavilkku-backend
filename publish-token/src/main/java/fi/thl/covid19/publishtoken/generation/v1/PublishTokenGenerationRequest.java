package fi.thl.covid19.publishtoken.generation.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import fi.thl.covid19.publishtoken.error.InputValidationException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static fi.thl.covid19.publishtoken.Validation.normalizeAndValidatePhoneNumber;
import static fi.thl.covid19.publishtoken.Validation.validateUserName;
import static java.util.Objects.requireNonNull;

public class PublishTokenGenerationRequest {
    public final String requestUser;
    public final LocalDate symptomsOnset;
    public final Optional<String> patientSmsNumber;
    public final boolean validateOnly;

    @JsonCreator
    public PublishTokenGenerationRequest(String requestUser,
                                         LocalDate symptomsOnset,
                                         Optional<String> patientSmsNumber,
                                         Optional<Boolean> validateOnly) {

        this.validateOnly = validateOnly.orElse(false);
        this.requestUser = validateUserName(requireNonNullInternal(requestUser, "User required", this.validateOnly), this.validateOnly);
        this.symptomsOnset = requireNonNullInternal(symptomsOnset, "Symptoms onset date required", this.validateOnly);
        this.patientSmsNumber = requireNonNullInternal(patientSmsNumber, "Phone number required", this.validateOnly).isPresent() ?
                Optional.of(normalizeAndValidatePhoneNumber(patientSmsNumber.get(), this.validateOnly)) : Optional.empty();
        if (symptomsOnset.isAfter(LocalDate.now(ZoneOffset.UTC).plus(1, ChronoUnit.DAYS))) {
            throw new InputValidationException("Symptoms onset time in the future: date=" + symptomsOnset, this.validateOnly);
        }
    }

    private static <T> T requireNonNullInternal(T obj, String message, boolean validateOnly) {
        try {
            requireNonNull(obj);
        } catch (NullPointerException npe) {
            if (validateOnly) {
                throw new InputValidationException(message, true);
            } else {
                throw npe;
            }
        }

        return obj;
    }
}
