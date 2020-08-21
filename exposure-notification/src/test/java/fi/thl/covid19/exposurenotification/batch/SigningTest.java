package fi.thl.covid19.exposurenotification.batch;

import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SigningTest {
    private static final String ALGORITHM = "SHA256withECDSA";
    private static final String TEST_PRIVATE_BASE64 =
            "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgj3XOZ/EVd5rcmSycACJ52NUfDxiwpwI/waoIh57GstahRANCAAQOh/vrTDcEd71nXBfUx59rPeXjIbM2nkitgL2AnzJ/guswJqXnD64LRiiU2zajVI+QQxPRHESOXCsy9Z1S3VdR";
    private static final String TEST_PUBLIC_BASE64 =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEDof760w3BHe9Z1wX1Mefaz3l4yGzNp5IrYC9gJ8yf4LrMCal5w+uC0YolNs2o1SPkEMT0RxEjlwrMvWdUt1XUQ==";

    @Test
    public void correctSignatureIsVerified() throws GeneralSecurityException {
        PrivateKey privateKey = Signing.privateKey(TEST_PRIVATE_BASE64);
        PublicKey publicKey = Signing.publicKey(TEST_PUBLIC_BASE64);
        byte[] payload = "Testing signature creation by signing this string.".getBytes(UTF_8);
        byte[] signature = Signing.sign(ALGORITHM, privateKey, payload);
        assertTrue(Signing.singatureMatches(ALGORITHM, publicKey, signature, payload));
    }

    @Test
    public void signatureWithIncorrectDataIsNotVerified() throws GeneralSecurityException {
        PrivateKey privateKey = Signing.privateKey(TEST_PRIVATE_BASE64);
        PublicKey publicKey = Signing.publicKey(TEST_PUBLIC_BASE64);
        byte[] payload1 = "Testing signature creation by signing this string.".getBytes(UTF_8);
        byte[] payload2 = "Testing signature creation by signing some string.".getBytes(UTF_8);
        byte[] signature = Signing.sign(ALGORITHM, privateKey, payload1);
        assertFalse(Signing.singatureMatches(ALGORITHM, publicKey, signature, payload2));
    }

    @Test
    public void signatureWithWrongKeyIsNotVerified() throws GeneralSecurityException {
        PrivateKey privateKey = Signing.privateKey(TEST_PRIVATE_BASE64);
        byte[] payload = "Testing signature creation by signing this string.".getBytes(UTF_8);
        byte[] signature = Signing.sign(ALGORITHM, privateKey, payload);
        assertFalse(Signing.singatureMatches(ALGORITHM, Signing.randomKeyPair().getPublic(), signature, payload));
    }
}
