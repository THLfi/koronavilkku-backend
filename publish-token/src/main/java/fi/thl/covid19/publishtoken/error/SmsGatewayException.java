package fi.thl.covid19.publishtoken.error;

public class SmsGatewayException extends RuntimeException {
    public SmsGatewayException() {
        super("SMS gateway error. Please try again later.");
    }
}
