package fi.thl.covid19.exposurenotification.efgs;

import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class UploadResponseEntity {

    public final HttpStatus httpStatus;
    public final Optional<Map<Integer, List<Integer>>> multiStatuses;

    public UploadResponseEntity(HttpStatus httpStatus, Optional<Map<Integer, List<Integer>>> multiStatuses) {
        this.httpStatus = httpStatus;
        this.multiStatuses = multiStatuses;
    }
}
