package fi.thl.covid19.exposurenotification.efgs;

import fi.thl.covid19.exposurenotification.efgs.entity.FederationOutboundOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
import static net.logstash.logback.argument.StructuredArguments.keyValue;

@Repository
public class OperationDao {

    private static final Logger LOG = LoggerFactory.getLogger(OperationDao.class);

    public enum EfgsOperationState {STARTED, FINISHED, ERROR}

    public enum EfgsOperationDirection {INBOUND, OUTBOUND}

    public static final long STALLED_MIN_AGE_IN_MINUTES = 10;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public OperationDao(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = requireNonNull(jdbcTemplate);
    }

    @Transactional
    public boolean finishOperation(long operationId, int keysCountTotal, int failedKeysCount, Optional<String> batchTag) {
        String sql = "update en.efgs_operation set state = cast(:new_state as en.state_t), batch_tag = :batch_tag, " +
                "keys_count_total = :keys_count_total, invalid_signature_count = :invalid_signature_count, " +
                "updated_at = :updated_at where id = :id";
        Map<String, Object> params = new HashMap<>();
        params.put("new_state", FINISHED.name());
        params.put("id", operationId);
        params.put("batch_tag", batchTag.orElse(null));
        params.put("keys_count_total", keysCountTotal);
        params.put("invalid_signature_count", failedKeysCount);
        params.put("updated_at", new Timestamp(Instant.now().toEpochMilli()));
        return jdbcTemplate.update(sql, params) == 1;
    }

    @Transactional
    public boolean finishOperation(FederationOutboundOperation operation, int keysCountTotal, int keysCount201, int keysCount409, int keysCount500) {
        String sql = "update en.efgs_operation set state = cast(:new_state as en.state_t), batch_tag = :batch_tag, " +
                "keys_count_total = :keys_count_total, keys_count_201 = :keys_count_201, " +
                "keys_count_409 = :keys_count_409, keys_count_500 = :keys_count_500, updated_at = :updated_at " +
                "where id = :id";
        boolean success = jdbcTemplate.update(sql, Map.of(
                "new_state", FINISHED.name(),
                "id", operation.operationId,
                "batch_tag", operation.batchTag,
                "keys_count_total", keysCountTotal,
                "keys_count_201", keysCount201,
                "keys_count_409", keysCount409,
                "keys_count_500", keysCount500,
                "updated_at", new Timestamp(Instant.now().toEpochMilli())
        )) == 1;
        LOG.info("Efgs sync finished. {} {} {} {}",
                keyValue("success", success),
                keyValue("operationId", operation.operationId),
                keyValue("batchTag", operation.batchTag),
                keyValue("keys", keysCountTotal));
        return success;
    }

    @Transactional
    public void markErrorOperation(long operationId, Optional<String> batchTag) {
        markErrorOperation(operationId, batchTag, new Timestamp(Instant.now().toEpochMilli()));
    }

    @Transactional
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
        LOG.info("Efgs sync failed. {} {}",
                keyValue("operationId", operationId),
                keyValue("batchTag", batchTag));
    }

    @Transactional
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

    @Transactional
    public long startOutboundOperation(EfgsOperationDirection direction, Timestamp timestamp) {
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

    @Transactional
    public Optional<Long> startInboundOperation(Optional<String> batchTag) {
        if (batchTag.isPresent()) {
            return startInboundOperation(new Timestamp(Instant.now().toEpochMilli()), batchTag.get());
        } else {
            return startInboundOperation(new Timestamp(Instant.now().toEpochMilli()));
        }
    }

    @Transactional
    public Optional<Long> startInboundOperation(Timestamp timestamp) {
        String createOperation = "insert into en.efgs_operation (state, direction, updated_at) " +
                "select cast(:state as en.state_t), cast(:direction as en.direction_t), :updated_at " +
                "where not exists ( " +
                "select 1 from en.efgs_operation where " +
                "direction = cast(:direction as en.direction_t) and " +
                "updated_at >= current_date::timestamp " +
                "and state <> cast(:error_state as en.state_t) for update skip locked " +
                ") " +
                "returning id";
        return jdbcTemplate.query(createOperation,
                Map.of(
                        "state", STARTED.name(),
                        "error_state", ERROR.name(),
                        "direction", INBOUND.name(),
                        "updated_at", timestamp
                ),
                (rs, i) -> rs.getLong(1))
                .stream().findFirst();
    }

    @Transactional
    public Optional<Long> startInboundOperation(Timestamp timestamp, String batchTag) {
        String createOperation = "insert into en.efgs_operation (state, direction, updated_at, batch_tag) " +
                "select cast(:state as en.state_t), cast(:direction as en.direction_t), :updated_at, :batch_tag " +
                "where not exists ( " +
                "select 1 from en.efgs_operation where " +
                "batch_tag = :batch_tag and " +
                "direction = cast(:direction as en.direction_t) and " +
                "updated_at >= current_date::timestamp " +
                "and state <> cast(:error_state as en.state_t) for update skip locked " +
                ") " +
                "returning id";
        return jdbcTemplate.query(createOperation,
                Map.of(
                        "state", STARTED.name(),
                        "error_state", ERROR.name(),
                        "direction", INBOUND.name(),
                        "updated_at", timestamp,
                        "batch_tag", batchTag
                ),
                (rs, i) -> rs.getLong(1))
                .stream().findFirst();
    }

    @Transactional
    public Map<String, Long> getInboundErrorBatchTags(LocalDate date) {
        String sql = "with finished as ( " +
                "select batch_tag from en.efgs_operation " +
                "where state = cast(:finished_state as en.state_t) and direction = cast(:direction as en.direction_t) " +
                "and updated_at > :start ) " +
                "select batch_tag from en.efgs_operation " +
                "where state = cast(:error_state as en.state_t) and direction = cast(:direction as en.direction_t) " +
                "and updated_at > :start and updated_at < :end and batch_tag not in (select batch_tag from finished) " +
                "for update skip locked";
        List<String> inboundErrors = jdbcTemplate.queryForList(sql,
                Map.of(
                        "finished_state", FINISHED.name(),
                        "error_state", ERROR.name(),
                        "direction", INBOUND.name(),
                        "start", Timestamp.valueOf(date.atStartOfDay()),
                        "end", Timestamp.valueOf(date.plusDays(1).atStartOfDay())
                ), String.class);

        return inboundErrors.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }
}
