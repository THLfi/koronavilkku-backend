package fi.thl.covid19.exposurenotification.efgs.signing;

import fi.thl.covid19.proto.EfgsProto;

import java.security.cert.X509Certificate;

public interface FederationGatewaySigning {

    String sign(final EfgsProto.DiagnosisKeyBatch data);
    X509Certificate getTrustAnchor();

}
