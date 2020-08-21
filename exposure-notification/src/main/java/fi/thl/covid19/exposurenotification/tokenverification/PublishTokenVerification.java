package fi.thl.covid19.exposurenotification.tokenverification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

import static java.util.Objects.requireNonNull;

public class PublishTokenVerification {
    public final int id;
    public final LocalDate symptomsOnset;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public PublishTokenVerification(@JsonProperty("id") int id,
                                    @JsonProperty("symptomsOnset") LocalDate symptomsOnset) {
        this.id = id;
        this.symptomsOnset = requireNonNull(symptomsOnset);
    }
}
