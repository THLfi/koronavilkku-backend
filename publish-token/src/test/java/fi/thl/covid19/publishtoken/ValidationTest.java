package fi.thl.covid19.publishtoken;

import fi.thl.covid19.publishtoken.error.InputValidationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static fi.thl.covid19.publishtoken.Validation.SERVICE_NAME_MAX_LENGTH;
import static fi.thl.covid19.publishtoken.Validation.USER_NAME_MAX_LENGTH;
import static net.logstash.logback.encoder.org.apache.commons.lang3.StringUtils.repeat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ValidationTest {

    @Test
    public void validPublishTokenIsAccepted() {
        assertDoesNotThrow(() -> Validation.validatePublishToken("000000000000"));
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
    public void validUserNameIsAccepted() {
        assertDoesNotThrow(() -> Validation.validateUserName("user"));
        assertDoesNotThrow(() -> Validation.validateUserName("Us-Erz1.09_"));
        assertDoesNotThrow(() -> Validation.validateUserName(repeat("a", USER_NAME_MAX_LENGTH)));
    }

    @Test
    public void invalidUserNameIsRejected() {
        assertThrows(InputValidationException.class, () -> Validation.validateUserName(""));
        assertThrows(InputValidationException.class, () -> Validation.validateUserName("us er"));
        assertThrows(InputValidationException.class, () -> Validation.validateUserName("us,er"));
        assertThrows(InputValidationException.class, () -> Validation.validateUserName("us'er"));
        assertThrows(InputValidationException.class, () -> Validation.validateUserName("us\"er"));
        assertThrows(InputValidationException.class, () -> Validation.validateUserName("us\ner"));
    }

    @Test
    public void tooLongUserNameIsRejected() {
        assertThrows(InputValidationException.class,
                () -> Validation.validateUserName(repeat("a", USER_NAME_MAX_LENGTH + 1)));
    }

    @Test
    public void emptyUserNameIsRejected() {
        assertThrows(InputValidationException.class, () -> Validation.validateUserName(""));
    }

    @Test
    public void validServiceNameIsAccepted() {
        assertDoesNotThrow(() -> Validation.validateServiceName("service"));
        assertDoesNotThrow(() -> Validation.validateServiceName("Zserv-iCe_1234.5678.90"));
        assertDoesNotThrow(() -> Validation.validateServiceName(repeat("a", SERVICE_NAME_MAX_LENGTH)));
    }

    @Test
    public void tooLongServiceNameIsRejected() {
        assertThrows(InputValidationException.class,
                () -> Validation.validateServiceName(repeat("a", SERVICE_NAME_MAX_LENGTH+1)));
    }

    @Test
    public void invalidServiceNameIsRejected() {
        assertThrows(InputValidationException.class, () -> Validation.validateServiceName(""));
        assertThrows(InputValidationException.class, () -> Validation.validateServiceName("ser vice"));
        assertThrows(InputValidationException.class, () -> Validation.validateServiceName("ser,vice"));
        assertThrows(InputValidationException.class, () -> Validation.validateServiceName("ser'vice"));
        assertThrows(InputValidationException.class, () -> Validation.validateServiceName("ser\"vice"));
        assertThrows(InputValidationException.class, () -> Validation.validateServiceName("ser\nvice"));
    }

    @Test
    public void emptyServiceNameIsRejected() {
        assertThrows(InputValidationException.class, () -> Validation.validateServiceName(""));
    }

    @Test
    public void phoneNumberValidationAcceptsNormalNumbers() {
        assertDoesNotThrow(() -> Validation.normalizeAndValidatePhoneNumber("123456"));
        assertDoesNotThrow(() -> Validation.normalizeAndValidatePhoneNumber("1234567"));
        assertDoesNotThrow(() -> Validation.normalizeAndValidatePhoneNumber("040-1234567"));
        assertDoesNotThrow(() -> Validation.normalizeAndValidatePhoneNumber("+358 40 123 4567"));
    }

    @Test
    public void phoneNumberValidationRejectsAlphabets() {
        assertThrows(InputValidationException.class, () -> Validation.normalizeAndValidatePhoneNumber("040-1234A67"));
        assertThrows(InputValidationException.class, () -> Validation.normalizeAndValidatePhoneNumber("+358 40 1B3 4567"));
        assertThrows(InputValidationException.class, () -> Validation.normalizeAndValidatePhoneNumber("1234c6"));
        assertThrows(InputValidationException.class, () -> Validation.normalizeAndValidatePhoneNumber("123456å"));
    }

    @Test
    public void phoneNumberValidationRejectsTooLongOrTooShort() {
        assertThrows(InputValidationException.class, () -> Validation.normalizeAndValidatePhoneNumber("12345"));
        assertDoesNotThrow(() -> Validation.normalizeAndValidatePhoneNumber("123456"));
        assertDoesNotThrow(() -> Validation.normalizeAndValidatePhoneNumber("123456789012345"));
        assertThrows(InputValidationException.class, () -> Validation.normalizeAndValidatePhoneNumber("1234567890123456"));
    }

    @Test
    public void phoneNumberNormalizationRemovesFormattingCharacters() {
        Assertions.assertEquals("123456", Validation.normalizeAndValidatePhoneNumber(" 1 2  3-- 45-6-()"));
    }
}
