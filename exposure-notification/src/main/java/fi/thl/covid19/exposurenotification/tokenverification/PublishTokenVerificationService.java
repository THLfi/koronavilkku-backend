package fi.thl.covid19.exposurenotification.tokenverification;

public interface PublishTokenVerificationService {
    PublishTokenVerification getVerification(String token);
}
