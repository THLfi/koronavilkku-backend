package fi.thl.covid19.exposurenotification.efgs.entity;

import java.time.ZonedDateTime;

import static java.util.Objects.requireNonNull;

public class AuditEntry {

    public final String country;
    public final ZonedDateTime uploadedTime;
    public final String uploaderThumbprint;
    public final String uploaderSigningThumbprint;
    public final String uploaderCertificate;
    public final long amount;
    public final String batchSignature;
    public final String uploaderOperatorSignature;
    public final String signingCertificateOperatorSignature;
    public final String signingCertificate;

    public AuditEntry(String country, ZonedDateTime uploadedTime, String uploaderThumbprint,
                      String uploaderCertificate, String uploaderSigningThumbprint,
                      long amount, String batchSignature, String uploaderOperatorSignature,
                      String signingCertificateOperatorSignature, String signingCertificate) {
        this.country = requireNonNull(country);
        this.uploadedTime = requireNonNull(uploadedTime);
        this.uploaderThumbprint = requireNonNull(uploaderThumbprint);
        this.uploaderSigningThumbprint = requireNonNull(uploaderSigningThumbprint);
        this.uploaderCertificate = requireNonNull(uploaderCertificate);
        this.amount = amount;
        this.batchSignature = requireNonNull(batchSignature);
        this.uploaderOperatorSignature = requireNonNull(uploaderOperatorSignature);
        this.signingCertificateOperatorSignature = requireNonNull(signingCertificateOperatorSignature);
        this.signingCertificate = requireNonNull(signingCertificate);
    }

    @Override
    public String toString() {
        return "AuditEntry{" +
                "country='" + country + '\'' +
                ", uploadedTime=" + uploadedTime +
                ", uploaderThumbprint='" + uploaderThumbprint + '\'' +
                ", uploaderSigningThumbprint='" + uploaderSigningThumbprint + '\'' +
                ", uploaderCertificate='" + uploaderCertificate + '\'' +
                ", amount=" + amount +
                ", batchSignature='" + batchSignature + '\'' +
                ", uploaderOperatorSignature='" + uploaderOperatorSignature + '\'' +
                ", signingCertificateOperatorSignature='" + signingCertificateOperatorSignature + '\'' +
                ", signingCertificate='" + signingCertificate + '\'' +
                '}';
    }
}
