package fi.thl.covid19.exposurenotification.efgs;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

@Repository
public class OperationDao {
    public enum EfgsOperationState {STARTED, FINISHED, ERROR}

    public enum EfgsOperationDirection {INBOUND, OUTBOUND}

    public static final long STALLED_MIN_AGE_IN_MINUTES = 10;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public OperationDao(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = requireNonNull(jdbcTemplate);
    }

    public boolean finishOperation(long operationId, int keysCountTotal) {
        String sql = "update en.efgs_operation set state = cast(:new_state as en.state_t), " +
                "keys_count_total = :keys_count_total, updated_at = :updated_at " +
                "where id = :id";
        return jdbcTemplate.update(sql, Map.of(
                "new_state", EfgsOperationState.FINISHED.name(),
                "id", operationId,
                "keys_count_total", keysCountTotal,
                "updated_at", new Timestamp(Instant.now().toEpochMilli())
        )) == 1;
    }

    public boolean finishOperation(long operationId, int keysCountTotal, int keysCount201, int keysCount409, int keysCount500) {
        String sql = "update en.efgs_operation set state = cast(:new_state as en.state_t), " +
                "keys_count_total = :keys_count_total, keys_count_201 = :keys_count_201, " +
                "keys_count_409 = :keys_count_409, keys_count_500 = :keys_count_500, updated_at = :updated_at " +
                "where id = :id";
        return jdbcTemplate.update(sql, Map.of(
                "new_state", EfgsOperationState.FINISHED.name(),
                "id", operationId,
                "keys_count_total", keysCountTotal,
                "keys_count_201", keysCount201,
                "keys_count_409", keysCount409,
                "keys_count_500", keysCount500,
                "updated_at", new Timestamp(Instant.now().toEpochMilli())
        )) == 1;
    }

    public void markErrorOperation(long operationId) {
        markErrorOperation(operationId, new Timestamp(Instant.now().toEpochMilli()));
    }

    public void markErrorOperation(long operationId, Timestamp timestamp) {
        String sql = "update en.efgs_operation set state = cast(:error_state as en.state_t), updated_at = :updated_at " +
                "where id = :id";
        jdbcTemplate.update(sql, Map.of(
                "error_state", EfgsOperationState.ERROR.name(),
                "updated_at", timestamp,
                "id", operationId
        ));
    }

    public List<Timestamp> getCrashed(EfgsOperationDirection direction) {
        String sql = "update en.efgs_operation set state = cast(:new_state as en.state_t) " +
                "where state = cast(:current_state as en.state_t) and direction = cast(:direction as en.direction_t) " +
                "and updated_at < :max_age returning updated_at";
        return jdbcTemplate.queryForList(sql, Map.of(
                "new_state", EfgsOperationState.ERROR.name(),
                "current_state", EfgsOperationState.STARTED.name(),
                "direction", direction.name(),
                "max_age", new Timestamp(Instant.now().minus(Duration.ofMinutes(STALLED_MIN_AGE_IN_MINUTES)).toEpochMilli())
        ), Timestamp.class);
    }

    public long startOperation(EfgsOperationDirection direction) {
        return startOperation(direction, new Timestamp(Instant.now().toEpochMilli()));
    }

    public long startOperation(EfgsOperationDirection direction, Timestamp timestamp) {
        String createOperation = "insert into en.efgs_operation (state, direction, updated_at) " +
                "values (cast(:state as en.state_t), cast(:direction as en.direction_t), :updated_at) returning id";
        return jdbcTemplate.query(createOperation,
                Map.of(
                        "state", EfgsOperationState.STARTED.name(),
                        "direction", direction.name(),
                        "updated_at", timestamp
                ),
                (rs, i) -> rs.getLong(1))
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("OperationId not returned."));
    }
}
