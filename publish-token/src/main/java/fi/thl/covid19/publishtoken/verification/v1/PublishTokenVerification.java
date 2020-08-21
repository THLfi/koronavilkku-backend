package fi.thl.covid19.publishtoken.verification.v1;

import java.time.LocalDate;

import static java.util.Objects.requireNonNull;

public class PublishTokenVerification {
    public final int id;
    public final LocalDate symptomsOnset;

    public PublishTokenVerification(int id, LocalDate symptomsOnset) {
        this.id = id;
        this.symptomsOnset = requireNonNull(symptomsOnset);
    }
}
