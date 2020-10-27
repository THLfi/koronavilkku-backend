package fi.thl.covid19.exposurenotification.diagnosiskey;

import fi.thl.covid19.exposurenotification.diagnosiskey.v1.DiagnosisPublishRequest;
import fi.thl.covid19.exposurenotification.diagnosiskey.v1.TemporaryExposureKeyRequest;
import fi.thl.covid19.exposurenotification.error.InputValidationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.dayFirst10MinInterval;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ValidationTest {

    private TestKeyGenerator keyGenerator;

    @BeforeEach
    public void setUp() {
        keyGenerator = new TestKeyGenerator(3465);
    }

    @Test
    public void validExposureKeyIsAccepted() {
        assertDoesNotThrow(
                () -> Validation.validateKeyData(Base64.getEncoder().encodeToString(new byte[16])));
        assertDoesNotThrow(() -> Validation.validateKeyData("AaBbCcZzDdA+/12349AAAA=="));
        assertDoesNotThrow(() -> Validation.validateKeyData("AAAAAAAAAAAAAAAAAAAAAA=="));
    }

    @Test
    public void tooShortExposureKeyIsRejected() {
        String short1 = Base64.getEncoder().encodeToString(new byte[0]);
        assertThrows(InputValidationException.class, () -> Validation.validateKeyData(short1));

        String short2 = Base64.getEncoder().encodeToString(new byte[8]);
        assertThrows(InputValidationException.class, () -> Validation.validateKeyData(short2));

        String short3 = Base64.getEncoder().encodeToString(new byte[15]);
        assertThrows(InputValidationException.class, () -> Validation.validateKeyData(short3));
    }

    @Test
    public void tooLongExposureKeyIsRejected() {
        String long1 = Base64.getEncoder().encodeToString(new byte[24]);
        assertThrows(InputValidationException.class, () -> Validation.validateKeyData(long1));

        String long2 = Base64.getEncoder().encodeToString(new byte[17]);
        assertThrows(InputValidationException.class, () -> Validation.validateKeyData(long2));
    }

    @Test
    public void nonBase64ExposureKeyIsRejected() {
        assertThrows(InputValidationException.class, () -> Validation.validateKeyData("AAAAAAAAAAA_AAAAAAAAAA=="));
        assertThrows(InputValidationException.class, () -> Validation.validateKeyData("AAAAAAAAAAA-AAAAAAAAAA=="));
        assertThrows(InputValidationException.class, () -> Validation.validateKeyData("AAAAAAAAAAA=AAAAAAAAAA=="));
        assertThrows(InputValidationException.class, () -> Validation.validateKeyData("AAAAAAAAAAA.AAAAAAAAAA=="));
        assertThrows(InputValidationException.class, () -> Validation.validateKeyData("AAAAAAAAAAA,AAAAAAAAAA=="));
        assertThrows(InputValidationException.class, () -> Validation.validateKeyData("AAAAAAAAAAA AAAAAAAAAA=="));
        assertThrows(InputValidationException.class, () -> Validation.validateKeyData("AAAAAAAAAAA`AAAAAAAAAA=="));
        assertThrows(InputValidationException.class, () -> Validation.validateKeyData("AAAAAAAAAAA´AAAAAAAAAA=="));
        assertThrows(InputValidationException.class, () -> Validation.validateKeyData("AAAAAAAAAAA'AAAAAAAAAA=="));
        assertThrows(InputValidationException.class, () -> Validation.validateKeyData("AAAAAAAAAAA\"AAAAAAAAAA=="));
        assertThrows(InputValidationException.class, () -> Validation.validateKeyData("AAAAAAAAAAA!AAAAAAAAAA=="));
    }

    @Test
    public void validPublishTokenIsAccepted() {
        Assertions.assertDoesNotThrow(() -> Validation.validatePublishToken("321654980654"));
    }

    @Test
    public void invalidPublishTokenIsRejected() {
        assertThrows(InputValidationException.class, () -> Validation.validatePublishToken("0A0000000000"));
        assertThrows(InputValidationException.class, () -> Validation.validatePublishToken("0_0000000000"));
        assertThrows(InputValidationException.class, () -> Validation.validatePublishToken("0-0000000000"));
        assertThrows(InputValidationException.class, () -> Validation.validatePublishToken("0=0000000000"));
        assertThrows(InputValidationException.class, () -> Validation.validatePublishToken("0.0000000000"));
        assertThrows(InputValidationException.class, () -> Validation.validatePublishToken("0,0000000000"));
        assertThrows(InputValidationException.class, () -> Validation.validatePublishToken("0 0000000000"));
        assertThrows(InputValidationException.class, () -> Validation.validatePublishToken(" 00000000000"));
        assertThrows(InputValidationException.class, () -> Validation.validatePublishToken("00000000000 "));
        assertThrows(InputValidationException.class, () -> Validation.validatePublishToken("00`000000000"));
        assertThrows(InputValidationException.class, () -> Validation.validatePublishToken("00´000000000"));
        assertThrows(InputValidationException.class, () -> Validation.validatePublishToken("00'000000000"));
        assertThrows(InputValidationException.class, () -> Validation.validatePublishToken("00\"000000000"));
        assertThrows(InputValidationException.class, () -> Validation.validatePublishToken("00!000000000"));
        assertThrows(InputValidationException.class, () -> Validation.validatePublishToken("0000000000000"));
        assertThrows(InputValidationException.class, () -> Validation.validatePublishToken("00000000000"));
    }

    @Test
    public void transmissionRiskRangeIsValidated() {
        assertDoesNotThrow(() -> Validation.validateTransmissionRiskLevel(0));
        assertDoesNotThrow(() -> Validation.validateTransmissionRiskLevel(8));
        assertThrows(InputValidationException.class, () -> Validation.validateTransmissionRiskLevel(-1));
        assertThrows(InputValidationException.class, () -> Validation.validateTransmissionRiskLevel(9));
    }

    @Test
    public void rollingStartIntervalRangeIsValidated() {
        assertDoesNotThrow(() -> Validation.validateRollingStartIntervalNumber(0));
        assertDoesNotThrow(() -> Validation.validateRollingStartIntervalNumber(Integer.MAX_VALUE));
        assertThrows(InputValidationException.class, () -> Validation.validateRollingPeriod(-1));
    }

    @Test
    public void rollingStartPeriodRangeIsValidated() {
        assertDoesNotThrow(() -> Validation.validateRollingPeriod(1));
        assertThrows(InputValidationException.class, () -> Validation.validateRollingPeriod(0));
        assertDoesNotThrow(() -> Validation.validateRollingPeriod(144));
        assertThrows(InputValidationException.class, () -> Validation.validateRollingPeriod(145));
    }

    @Test
    public void publishRejectsTooSmallPost() {
        assertThrows(InputValidationException.class, () -> new DiagnosisPublishRequest(keyGenerator.someRequestKeys(13)));
    }

    @Test
    public void publishAcceptsCorrectPost() {
        assertDoesNotThrow(() -> new DiagnosisPublishRequest(keyGenerator.someRequestKeys(14)));
    }

    @Test
    public void publishRejectsTooLargePost() {
        assertThrows(InputValidationException.class, () -> new DiagnosisPublishRequest(keyGenerator.someRequestKeys(15)));
    }

    @Test
    public void publishRequestAcceptsDummyPost() {
        assertDoesNotThrow(() -> {
            List<TemporaryExposureKeyRequest> keys = new ArrayList<>();
            for (int i = 0; i < 14; i++) {
                keys.add(new TemporaryExposureKeyRequest(
                        "AAAAAAAAAAAAAAAAAAAAAA==",
                        0,
                        dayFirst10MinInterval(Instant.now()),
                        IntervalNumber.INTERVALS_10MIN_PER_24H,
                        Optional.empty()
                ));
            }
            new DiagnosisPublishRequest(keys);
        });
    }
}
