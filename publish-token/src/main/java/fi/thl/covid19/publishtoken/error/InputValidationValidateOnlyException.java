package fi.thl.covid19.publishtoken.error;

@Deprecated
public class InputValidationValidateOnlyException extends RuntimeException {
    public InputValidationValidateOnlyException(String msg) {
        super(msg);
    }
}
