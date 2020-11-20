package fi.thl.covid19.exposurenotification.efgs;

public class Callback {

    public final String callbackId;
    public final String url;

    public Callback(String callbackId, String url) {
        this.callbackId = callbackId;
        this.url = url;
    }
}
