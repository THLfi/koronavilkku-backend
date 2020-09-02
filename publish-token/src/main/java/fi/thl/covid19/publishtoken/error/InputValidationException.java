package fi.thl.covid19.publishtoken.error;

public class InputValidationException extends RuntimeException {

    private final boolean validateOnly;

    public InputValidationException(String message, boolean validateOnly) {
        super(message);
        this.validateOnly = validateOnly;
    }

    public boolean isValidateOnly() {
        return validateOnly;
    }
}
