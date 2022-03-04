package fi.thl.covid19.exposurenotification.configuration.v2;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Map;

public class EndOfLifeStatistic {

    public final Map<String, String> value;
    public final Map<String, String> label;

    @JsonCreator
    public EndOfLifeStatistic(Map<String, String> value, Map<String, String> label) {
        this.value = value;
        this.label = label;
    }
}
