package fi.thl.covid19.publishtoken;

import fi.thl.covid19.publishtoken.error.InputValidationException;

public final class Validation {
    private Validation() {
    }

    private static final String TOKEN_REGEX = "[0-9]{12}";

    private static final String PHONE_NUMBER_REGEX = "(\\+[0-9]{3})?[0-9]{6,15}";
    private static final String PHONE_NUMBER_FILLER_REGEX = "[- ()]";

    public static final int USER_NAME_MAX_LENGTH = 50;
    public static final int SERVICE_NAME_MAX_LENGTH = 100;
    private static final String NAME_REGEX = "([A-Za-z0-9\\-_.]+)";

    public static String validatePublishToken(String publishToken) {
        if (!publishToken.matches(TOKEN_REGEX)) {
            throw new InputValidationException("Invalid publish token.");
        }
        return publishToken;
    }

    public static String normalizeAndValidatePhoneNumber(String phoneNumber) {
        // Remove dashes, paranthesis and spaces. We don't care to validate these
        String normalized = phoneNumber.replaceAll(PHONE_NUMBER_FILLER_REGEX, "");
        if (!normalized.matches(PHONE_NUMBER_REGEX)) {
            throw new InputValidationException("Invalid phone number");
        }
        return normalized;
    }

    public static String validateUserName(String userName) {
        return validateNameString("user name", userName, USER_NAME_MAX_LENGTH);
    }

    public static String validateServiceName(String serviceName) {
        return validateNameString("service name", serviceName, SERVICE_NAME_MAX_LENGTH);
    }

    private static String validateNameString(String description, String nameString, int maxLength) {
        if (nameString.length() > maxLength) {
            throw new InputValidationException("Too long " + description + " { " + nameString + " }");
        }
        if (!nameString.matches(NAME_REGEX)) {
            throw new InputValidationException("Invalid " + description + " { " + nameString + " } format");
        }
        return nameString;
    }
}
