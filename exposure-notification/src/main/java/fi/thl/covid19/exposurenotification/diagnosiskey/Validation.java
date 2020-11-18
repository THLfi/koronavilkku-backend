package fi.thl.covid19.exposurenotification.diagnosiskey;

import fi.thl.covid19.exposurenotification.error.InputValidationException;

import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class Validation {
    private Validation() {}

    private static final String TOKEN_REGEX = "[0-9]{12}";

    // Base64 string length, each char representing 6 bits
    private static final int KEY_DATA_LENGTH_BASE64 = 24;
    private static final int KEY_DATA_LENGTH_BYTES = 16;

    private static final int MIN_TRANSMISSION_RISK = 0;
    private static final int MAX_TRANSMISSION_RISK = 8;

    private static final int MIN_DAYS_SINCE_ONSET_OF_SYMPTOMS = -14;
    private static final int MAX_DAYS_SINCE_ONSET_OF_SYMPTOMS = 14;

    private static final int MIN_ROLLING_START_INTEVAL_NUMBER = 0;
    private static final int MAX_ROLLING_START_INTEVAL_NUMBER = Integer.MAX_VALUE;

    private static final int MIN_ROLLING_PERIOD = 1;
    private static final int MAX_ROLLING_PERIOD = 144;

    public static String validatePublishToken(String publishToken) {
        if (!publishToken.matches(TOKEN_REGEX)) {
            throw new InputValidationException("Invalid publish token");
        }
        return publishToken;
    }

    public static String validateKeyData(String keyData) {
        if (keyData.length() != KEY_DATA_LENGTH_BASE64) {
            throw new InputValidationException("Invalid encoded exposure key length: "
                    + keyData.length() + "!=" + KEY_DATA_LENGTH_BASE64);
        }
        // Try decoding -> will throw IllegalArgumentException if data is not valid
        try {
            byte[] decoded = Base64.getDecoder().decode(keyData.getBytes(UTF_8));
            if (decoded.length != KEY_DATA_LENGTH_BYTES) {
                throw new InputValidationException("Invalid decoded exposure key length: "
                        + decoded.length + "!=" + KEY_DATA_LENGTH_BYTES);
            }
        } catch (IllegalArgumentException e) {
            throw new InputValidationException("Invalid exposure key: not Base64");
        }
        return keyData;
    }

    public static int validateTransmissionRiskLevel(int transmissionRiskLevel) {
        return verifyValueBetween(
                "transmission risk level", transmissionRiskLevel,
                MIN_TRANSMISSION_RISK, MAX_TRANSMISSION_RISK);
    }

    public static int validateRollingStartIntervalNumber(int rollingStartIntervalNumber) {
        return verifyValueBetween(
                "rolling start interval", rollingStartIntervalNumber,
                MIN_ROLLING_START_INTEVAL_NUMBER, MAX_ROLLING_START_INTEVAL_NUMBER);
    }

    public static int validateRollingPeriod(int rollingPeriod) {
        return verifyValueBetween(
                "rolling period", rollingPeriod, MIN_ROLLING_PERIOD, MAX_ROLLING_PERIOD);
    }

    public static Set<String> validateISOCountryCodes(Set<String> codes) {
        return codes.stream().filter(Validation::validateISOCountryCode).collect(Collectors.toSet());
    }

    public static Set<String> validateISOCountryCodesWithoutFI(Set<String> codes) {
        return codes.stream().filter(code -> !code.equals("FI")).filter(Validation::validateISOCountryCode).collect(Collectors.toSet());
    }

    public static String getValidatedISOCountryCode(String code) {
        if (validateISOCountryCode(code)) {
            return code;
        } else {
            throw new IllegalStateException("Unknown country code.");
        }
    }

    public static boolean validateISOCountryCode(String code) {
            return Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2).contains(code);
    }

    public static int validateDaysSinceOnsetOfSymptoms(int daysSinceOnsetOfSymptoms) {
        return verifyValueBetween(
                "days since onset of symptoms", daysSinceOnsetOfSymptoms,
                MIN_DAYS_SINCE_ONSET_OF_SYMPTOMS, MAX_DAYS_SINCE_ONSET_OF_SYMPTOMS);
    }

    private static int verifyValueBetween(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new InputValidationException("Invalid " + name + ": value=" + value + " min=" + min + " max=" + max);
        }
        return value;
    }
}
