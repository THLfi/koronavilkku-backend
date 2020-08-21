package fi.thl.covid19.publishtoken.generation.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import fi.thl.covid19.publishtoken.Validation;
import fi.thl.covid19.publishtoken.error.InputValidationException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

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
        this.requestUser = validateUserName(requireNonNull(requestUser, "User required"));
        this.symptomsOnset = requireNonNull(symptomsOnset, "Symptoms onset date required");
        this.patientSmsNumber = requireNonNull(patientSmsNumber).map(Validation::normalizeAndValidatePhoneNumber);
        if (symptomsOnset.isAfter(LocalDate.now(ZoneOffset.UTC).plus(1, ChronoUnit.DAYS))) {
            throw new InputValidationException("Symptoms onset time in the future: date=" + symptomsOnset);
        }
        this.validateOnly = validateOnly.orElse(false);
    }
}
