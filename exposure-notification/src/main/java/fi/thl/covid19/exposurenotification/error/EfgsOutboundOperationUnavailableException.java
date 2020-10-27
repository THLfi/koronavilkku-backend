package fi.thl.covid19.exposurenotification.error;

public class EfgsOutboundOperationUnavailableException extends RuntimeException {
    public EfgsOutboundOperationUnavailableException(String message) {
        super(message);
    }
}
