package fi.thl.covid19.exposurenotification.error;

public class EfgsOperationException extends RuntimeException {
    public EfgsOperationException(String message, Throwable t) {
        super(message, t);
    }
}
