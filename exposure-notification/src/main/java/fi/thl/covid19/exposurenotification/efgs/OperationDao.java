package fi.thl.covid19.exposurenotification.efgs;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import static java.util.Objects.requireNonNull;

@Repository
public class OperationDao {
    public enum EfgsOperationState {STARTED, FINISHED, ERROR}

    public enum EfgsOperationDirection {INBOUND, OUTBOUND}

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public OperationDao(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = requireNonNull(jdbcTemplate);
    }

    public boolean finishOperation(long operationId, int keysCountTotal) {
        String sql = "update en.efgs_operation set state = cast(:new_state as en.state_t), keys_count_total = :keys_count_total " +
                "where id = :id";
        return jdbcTemplate.update(sql, Map.of(
                "new_state", EfgsOperationState.FINISHED.name(),
                "id", operationId,
                "keys_count_total", keysCountTotal
        )) == 1;
    }

    public boolean finishOperation(long operationId, int keysCountTotal, int keysCount201, int keysCount409, int keysCount500) {
        String sql = "update en.efgs_operation set state = cast(:new_state as en.state_t), " +
                "keys_count_total = :keys_count_total, keys_count_201 = :keys_count_201, " +
                "keys_count_409 = :keys_count_409, keys_count_500 = :keys_count_500 " +
                "where id = :id";
        return jdbcTemplate.update(sql, Map.of(
                "new_state", EfgsOperationState.FINISHED.name(),
                "id", operationId,
                "keys_count_total", keysCountTotal,
                "keys_count_201", keysCount201,
                "keys_count_409", keysCount409,
                "keys_count_500", keysCount500
        )) == 1;
    }

    public void markErrorOperation(long operationId) {
        String sql = "update en.efgs_operation set state = cast(:error_state as en.state_t), updated_at = :updated_at " +
                "where id = :id";
        jdbcTemplate.update(sql, Map.of(
                "error_state", EfgsOperationState.ERROR.name(),
                "updated_at", new Timestamp(Instant.now().toEpochMilli()),
                "id", operationId
        ));
    }

    public long startOperation(EfgsOperationDirection direction) {
        String createOperation = "insert into en.efgs_operation (state, direction) " +
                "values (cast(:state as en.state_t), cast(:direction as en.direction_t)) returning id";
        return jdbcTemplate.query(createOperation,
                Map.of(
                        "state", EfgsOperationState.STARTED.name(),
                        "direction", direction.name()),
                (rs, i) -> rs.getLong(1))
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("OperationId not returned."));
    }
}
