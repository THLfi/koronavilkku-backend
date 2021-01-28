package fi.thl.covid19.publishtoken.verification.v1;

import java.time.LocalDate;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class PublishTokenVerification {
    public final int id;
    public final LocalDate symptomsOnset;
    public final Optional<Boolean> symptomsExist;

    public PublishTokenVerification(int id, LocalDate symptomsOnset, Optional<Boolean> symptomsExist) {
        this.id = id;
        this.symptomsOnset = requireNonNull(symptomsOnset);
        this.symptomsExist = requireNonNull(symptomsExist);
    }
}
