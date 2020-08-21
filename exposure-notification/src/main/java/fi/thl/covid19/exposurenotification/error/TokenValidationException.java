package fi.thl.covid19.exposurenotification.error;

public class TokenValidationException extends RuntimeException {
    public TokenValidationException() {
        super("Publish token not accepted");
    }
}
