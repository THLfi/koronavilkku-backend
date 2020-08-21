package fi.thl.covid19.exposurenotification.error;

public class InputValidationException extends RuntimeException {
    public InputValidationException(String message) {
        super(message);
    }
}
