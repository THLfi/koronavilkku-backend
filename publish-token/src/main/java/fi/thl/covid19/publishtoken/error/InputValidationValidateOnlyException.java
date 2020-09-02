package fi.thl.covid19.publishtoken.error;

public class InputValidationValidateOnlyException extends RuntimeException {
    public InputValidationValidateOnlyException(String msg) {
        super(msg);
    }
}
