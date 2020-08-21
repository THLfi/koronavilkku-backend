package fi.thl.covid19.exposurenotification.error;


import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class ApiError {

    public final int errorId;
    public final int code;
    public final Optional<String> message;

    public ApiError(int errorId, int code, Optional<String> message) {
        this.errorId = errorId;
        this.code = code;
        this.message = requireNonNull(message);
    }
}
