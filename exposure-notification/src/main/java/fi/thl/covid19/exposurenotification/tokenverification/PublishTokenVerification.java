package fi.thl.covid19.exposurenotification.tokenverification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class PublishTokenVerification {
    public final int id;
    public final LocalDate symptomsOnset;
    public final Optional<Boolean> symptomsExist;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public PublishTokenVerification(@JsonProperty("id") int id,
                                    @JsonProperty("symptomsOnset") LocalDate symptomsOnset,
                                    @JsonProperty("symptomsExist") Optional<Boolean> symptomsExist
    ) {
        this.id = id;
        this.symptomsOnset = requireNonNull(symptomsOnset);
        this.symptomsExist = requireNonNull(symptomsExist);
    }
}
