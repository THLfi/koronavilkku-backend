package fi.thl.covid19.publishtoken.sms;

import java.io.Serializable;
import java.util.Set;

public class SmsPayload implements Serializable {

    public final String sender;
    public final String text;
    public final Set<String> destination;

    public SmsPayload(String sender, String text, Set<String> destination) {
        this.sender = sender;
        this.text = text;
        this.destination = destination;
    }
}
