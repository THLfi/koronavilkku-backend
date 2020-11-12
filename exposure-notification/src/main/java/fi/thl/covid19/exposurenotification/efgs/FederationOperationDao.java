package fi.thl.covid19.exposurenotification.efgs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

@Repository
public class FederationOperationDao {

    private static final Logger LOG = LoggerFactory.getLogger(FederationOperationDao.class);

    public enum EfgsOperationState {QUEUED, STARTED, FINISHED, ERROR}
    public enum EfgsOperationDirection {INBOUND, OUTBOUND}
    private static final long MAX_RUN_COUNT = 2;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FederationOperationDao(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = requireNonNull(jdbcTemplate);
    }

    public long getQueueId() {
        String sql = "select id from en.efgs_operation where state = cast(:state as en.state_t) and " +
                "direction = cast(:direction as en.direction_t)";
        return requireNonNull(jdbcTemplate.queryForObject(sql, Map.of(
                "state", FederationOperationDao.EfgsOperationState.QUEUED.name(),
                "direction", FederationOperationDao.EfgsOperationDirection.OUTBOUND.name()
        ), Long.class));
    }

    @Transactional(timeout = 10)
    public Optional<Long> startOutboundOperation() {
        String lock = "lock table en.efgs_operation in access exclusive mode";
        jdbcTemplate.update(lock, Map.of());
        if (isOutboundOperationAvailable()) {
            long operationId = this.getQueueId();
            markOutboundOperationStartedAndCreateNewQueue(operationId);
            return Optional.of(operationId);
        } else {
            LOG.info("Update to efgs unavailble. Skipping.");
            return Optional.empty();
        }
    }

    public boolean finishOperation(long operationId, int keysCountTotal) {
        String sql = "update en.efgs_operation set state = cast(:new_state as en.state_t), keys_count_total = :keys_count_total, " +
                "run_count = run_count + 1 " +
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
                "keys_count_409 = :keys_count_409, keys_count_500 = :keys_count_500, " +
                "run_count = run_count + 1 " +
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
        String sql = "update en.efgs_operation set state = cast(:error_state as en.state_t), updated_at = :updated_at, " +
                "run_count = run_count + 1 " +
                "where id = :id";
        jdbcTemplate.update(sql, Map.of(
                "error_state", EfgsOperationState.ERROR.name(),
                "updated_at", new Timestamp(Instant.now().toEpochMilli()),
                "id", operationId
        ));
    }

    public void setStalledToError() {
        String sql = "update en.efgs_operation set state = cast(:error_state as en.state_t), updated_at = :updated_at " +
                "where state = cast(:started_state as en.state_t) and (now() > (updated_at + interval '10 minute')) ";
        jdbcTemplate.update(sql, Map.of(
                "error_state", EfgsOperationState.ERROR.name(),
                "started_state", EfgsOperationState.STARTED.name(),
                "updated_at", new Timestamp(Instant.now().toEpochMilli())
        ));
    }

    public List<Long> getOutboundOperationsInError() {
        String sql = "select id from en.efgs_operation " +
                "where state = cast(:state as en.state_t) " +
                "and direction = cast(:direction as en.direction_t) " +
                "and run_count < :run_count";
        return jdbcTemplate.queryForList(sql, Map.of(
                "state", EfgsOperationState.ERROR.name(),
                "direction", EfgsOperationDirection.OUTBOUND.name(),
                "run_count", MAX_RUN_COUNT
        ), Long.class);
    }

    public long startInboundOperation() {
        KeyHolder operationKeyHolder = new GeneratedKeyHolder();

        String createOperation = "insert into en.efgs_operation (state, direction) " +
                "values (cast(:state as en.state_t), cast(:direction as en.direction_t))";
        jdbcTemplate.update(createOperation,
                new MapSqlParameterSource(Map.of(
                        "state", EfgsOperationState.STARTED.name(),
                        "direction", EfgsOperationDirection.INBOUND.name())
                ),
                operationKeyHolder);
        return (Long) requireNonNull(operationKeyHolder.getKeys()).get("id");
    }

    private boolean isOutboundOperationAvailable() {
        String sql = "select count(*) from en.efgs_operation " +
                "where state = cast(:state as en.state_t) and direction = cast(:direction as en.direction_t)";
        return jdbcTemplate.query(sql, Map.of(
                "state", EfgsOperationState.STARTED.name(),
                "direction", EfgsOperationDirection.OUTBOUND.name()
        ), (rs, i) -> rs.getInt(1))
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("Count returned nothing.")) == 0;
    }

    private void markOutboundOperationStartedAndCreateNewQueue(long operationId) {
        String startQueuedOperation = "update en.efgs_operation " +
                "set state = cast(:state as en.state_t), " +
                "updated_at = :updated_at " +
                "where id = :id";

        jdbcTemplate.update(startQueuedOperation, Map.of(
                "state", EfgsOperationState.STARTED.name(),
                "updated_at", new Timestamp(Instant.now().toEpochMilli()),
                "id", operationId)
        );

        String createNewQueueOperation = "insert into en.efgs_operation (state, direction) " +
                "values (cast(:state as en.state_t), cast(:direction as en.direction_t))";
        jdbcTemplate.update(createNewQueueOperation, Map.of(
                "state", EfgsOperationState.QUEUED.name(),
                "direction", EfgsOperationDirection.OUTBOUND.name()
        ));
    }
}
