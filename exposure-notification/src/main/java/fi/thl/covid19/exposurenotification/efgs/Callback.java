package fi.thl.covid19.exposurenotification.efgs;

import static java.util.Objects.requireNonNull;

public class Callback {

    public final String callbackId;
    public final String url;

    public Callback(String callbackId, String url) {
        this.callbackId = requireNonNull(callbackId);
        this.url = requireNonNull(url);
    }
}
