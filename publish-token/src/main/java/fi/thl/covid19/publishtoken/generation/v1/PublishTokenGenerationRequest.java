package fi.thl.covid19.publishtoken.generation.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import fi.thl.covid19.publishtoken.Validation;
import fi.thl.covid19.publishtoken.error.InputValidationException;
import fi.thl.covid19.publishtoken.error.InputValidationValidateOnlyException;

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
    @Deprecated
    public final boolean validateOnly;
    public final Optional<Boolean> symptomsExists;

    @JsonCreator
    public PublishTokenGenerationRequest(String requestUser,
                                         LocalDate symptomsOnset,
                                         Optional<String> patientSmsNumber,
                                         Optional<Boolean> validateOnly,
                                         Optional<Boolean> symptomsExists) {

        this.validateOnly = validateOnly.orElse(false);

        try {
            this.requestUser = validateUserName(requireNonNull(requestUser, "User required"));
            this.symptomsOnset = requireNonNull(symptomsOnset, "Symptoms onset date required");
            this.patientSmsNumber = requireNonNull(patientSmsNumber).map(Validation::normalizeAndValidatePhoneNumber);
            this.symptomsExists = requireNonNull(symptomsExists);
            if (symptomsOnset.isAfter(LocalDate.now(ZoneOffset.UTC).plus(1, ChronoUnit.DAYS))) {
                throw new InputValidationException("Symptoms onset time in the future: date=" + symptomsOnset);
            }
        } catch (InputValidationException | NullPointerException e) {
            if (this.validateOnly) {
                throw new InputValidationValidateOnlyException("ValidateOnly: " + e.getMessage());
            } else {
                throw e;
            }
        }
    }
}
