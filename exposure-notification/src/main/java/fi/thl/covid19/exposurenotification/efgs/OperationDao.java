package fi.thl.covid19.exposurenotification.efgs;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static fi.thl.covid19.exposurenotification.efgs.OperationDao.EfgsOperationDirection.*;
import static fi.thl.covid19.exposurenotification.efgs.OperationDao.EfgsOperationState.*;
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

    public boolean finishOperation(long operationId, int keysCountTotal, Optional<String> batchTag) {
        String sql = "update en.efgs_operation set state = cast(:new_state as en.state_t), batch_tag = :batch_tag, " +
                "keys_count_total = :keys_count_total, updated_at = :updated_at " +
                "where id = :id";
        Map<String, Object> params = new HashMap<>();
        params.put("new_state", FINISHED.name());
        params.put("id", operationId);
        params.put("batch_tag", batchTag.orElse(null));
        params.put("keys_count_total", keysCountTotal);
        params.put("updated_at", new Timestamp(Instant.now().toEpochMilli()));
        return jdbcTemplate.update(sql, params) == 1;
    }

    public boolean finishOperation(FederationOutboundOperation operation, int keysCountTotal, int keysCount201, int keysCount409, int keysCount500) {
        String sql = "update en.efgs_operation set state = cast(:new_state as en.state_t), batch_tag = :batch_tag, " +
                "keys_count_total = :keys_count_total, keys_count_201 = :keys_count_201, " +
                "keys_count_409 = :keys_count_409, keys_count_500 = :keys_count_500, updated_at = :updated_at " +
                "where id = :id";
        return jdbcTemplate.update(sql, Map.of(
                "new_state", FINISHED.name(),
                "id", operation.operationId,
                "batch_tag", operation.batchTag,
                "keys_count_total", keysCountTotal,
                "keys_count_201", keysCount201,
                "keys_count_409", keysCount409,
                "keys_count_500", keysCount500,
                "updated_at", new Timestamp(Instant.now().toEpochMilli())
        )) == 1;
    }

    public void markErrorOperation(long operationId, Optional<String> batchTag) {
        markErrorOperation(operationId, batchTag, new Timestamp(Instant.now().toEpochMilli()));
    }

    public void markErrorOperation(long operationId, Optional<String> batchTag, Timestamp timestamp) {
        String sql = "update en.efgs_operation set " +
                "state = cast(:error_state as en.state_t), " +
                "updated_at = :updated_at, " +
                "batch_tag = :batch_tag " +
                "where id = :id";
        Map<String, Object> params = new HashMap<>();
        params.put("error_state", ERROR.name());
        params.put("updated_at", timestamp);
        params.put("batch_tag", batchTag.orElse(null));
        params.put("id", operationId);
        jdbcTemplate.update(sql, params);
    }

    public List<Timestamp> getAndResolveCrashed(EfgsOperationDirection direction) {
        String sql = "update en.efgs_operation set state = cast(:new_state as en.state_t) " +
                "where state = cast(:current_state as en.state_t) and direction = cast(:direction as en.direction_t) " +
                "and updated_at < :stalled_operation_limit returning updated_at";
        return jdbcTemplate.queryForList(sql, Map.of(
                "new_state", ERROR.name(),
                "current_state", STARTED.name(),
                "direction", direction.name(),
                "stalled_operation_limit",
                new Timestamp(Instant.now().minus(Duration.ofMinutes(STALLED_MIN_AGE_IN_MINUTES)).toEpochMilli())
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
                        "state", STARTED.name(),
                        "direction", direction.name(),
                        "updated_at", timestamp
                ),
                (rs, i) -> rs.getLong(1))
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("OperationId not returned."));
    }

    public Map<String, Long> getInboundErrorBatchTags(LocalDate date) {
        String sql = "with finished as ( " +
                "select batch_tag from en.efgs_operation " +
                "where state = cast(:finished_state as en.state_t) and direction = cast(:direction as en.direction_t) " +
                "and updated_at > :start ) " +
                "select batch_tag from en.efgs_operation " +
                "where state = cast(:error_state as en.state_t) and direction = cast(:direction as en.direction_t) " +
                "and updated_at > :start and updated_at < :end and batch_tag not in (select batch_tag from finished)";
        List<String> inboundErrors = jdbcTemplate.queryForList(sql,
                Map.of(
                        "finished_state", FINISHED.name(),
                        "error_state", ERROR.name(),
                        "direction", INBOUND.name(),
                        "start", Timestamp.valueOf(date.atStartOfDay()),
                        "end", Timestamp.valueOf(date.atStartOfDay().plus(Duration.ofDays(1)))
                ), String.class);

        return inboundErrors.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }
}