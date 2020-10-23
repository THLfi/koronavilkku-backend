package fi.thl.covid19.exposurenotification.error;

public class EfgsUpdateException extends RuntimeException {
    public EfgsUpdateException(String message, Throwable t) {
        super(message, t);
    }
}
