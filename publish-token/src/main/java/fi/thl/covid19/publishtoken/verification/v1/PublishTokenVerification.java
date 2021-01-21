package fi.thl.covid19.publishtoken.verification.v1;

import java.time.LocalDate;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class PublishTokenVerification {
    public final int id;
    public final LocalDate symptomsOnset;
    public final Optional<Boolean> symptomsExists;

    public PublishTokenVerification(int id, LocalDate symptomsOnset, Optional<Boolean> symptomsExists) {
        this.id = id;
        this.symptomsOnset = requireNonNull(symptomsOnset);
        this.symptomsExists = requireNonNull(symptomsExists);
    }
}
