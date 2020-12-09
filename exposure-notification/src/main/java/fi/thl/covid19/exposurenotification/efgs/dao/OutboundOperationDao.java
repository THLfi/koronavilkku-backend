package fi.thl.covid19.exposurenotification.efgs.dao;

import fi.thl.covid19.exposurenotification.efgs.entity.OutboundOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static fi.thl.covid19.exposurenotification.efgs.util.CommonConst.EfgsOperationState.FINISHED;
import static fi.thl.covid19.exposurenotification.efgs.util.CommonConst.EfgsOperationState.STARTED;
import static fi.thl.covid19.exposurenotification.efgs.util.CommonConst.EfgsOperationState.ERROR;
import static fi.thl.covid19.exposurenotification.efgs.util.CommonConst.STALLED_MIN_AGE_IN_MINUTES;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.Objects.requireNonNull;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

@Repository
public class OutboundOperationDao {

    private static final Logger LOG = LoggerFactory.getLogger(OutboundOperationDao.class);
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public OutboundOperationDao(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = requireNonNull(jdbcTemplate);
    }

    @Transactional
    public long startOutboundOperation(Timestamp timestamp) {
        String createOperation = "insert into en.efgs_outbound_operation (state, updated_at) " +
                "values (cast(:state as en.state_t), :updated_at) returning id";
        return jdbcTemplate.query(createOperation,
                Map.of(
                        "state", STARTED.name(),
                        "updated_at", timestamp
                ),
                (rs, i) -> rs.getLong(1))
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("OperationId not returned."));
    }

    @Transactional
    public boolean finishOperation(OutboundOperation operation, int keysCountTotal, int keysCount201, int keysCount409, int keysCount500) {
        String sql = "update en.efgs_outbound_operation set state = cast(:new_state as en.state_t), batch_tag = :batch_tag, " +
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
        String sql = "update en.efgs_outbound_operation set " +
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
        LOG.info("Efgs outbound sync failed. {} {}",
                keyValue("operationId", operationId),
                keyValue("batchTag", batchTag));
    }

    @Transactional
    public List<Timestamp> getAndResolveStarted() {
        String sql = "update en.efgs_outbound_operation set state = cast(:new_state as en.state_t) " +
                "where state = cast(:current_state as en.state_t) " +
                "and updated_at < :stalled_operation_limit returning updated_at";
        return jdbcTemplate.queryForList(sql, Map.of(
                "new_state", ERROR.name(),
                "current_state", STARTED.name(),
                "stalled_operation_limit",
                Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).minus(STALLED_MIN_AGE_IN_MINUTES, MINUTES))
        ), Timestamp.class);
    }

    @Transactional
    public int getNumberOfErrorsForDay() {
        String sql = "select count(*) from en.efgs_outbound_operation where " +
                "and updated_at >= :datetime and state = cast(:error_state as en.state_t)";
        return jdbcTemplate.query(
                sql,
                Map.of("error_state", ERROR.name(),
                        "datetime", Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).minus(1, DAYS))
                ),
                (rs, i) -> rs.getInt(1)).stream().findFirst().orElseThrow(() -> new IllegalStateException("Count returned nothing."));
    }

    @Transactional
    public boolean checkEnSchemaExists() {
        String sql = "select count(*) from information_schema.schemata where schema_name = :schema_name";
        try {
            return jdbcTemplate.query(sql, Map.of("schema_name", "en"),
                    (rs, i) -> rs.getInt(1)).stream().findFirst().orElseThrow(() -> new IllegalStateException("Count returned nothing.")) > 0;
        } catch (DataAccessException | IllegalStateException e) {
            return false;
        }
    }
}
