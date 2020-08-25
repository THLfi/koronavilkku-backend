package fi.thl.covid19.publishtoken.error;


import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class ApiError {

    public final String errorId;
    public final int code;
    public final Optional<String> message;

    public ApiError(String errorId, int code, Optional<String> message) {
        this.errorId = requireNonNull(errorId);
        this.code = code;
        this.message = requireNonNull(message);
    }
}
