package fi.thl.covid19.exposurenotification.efgs.dao;

import fi.thl.covid19.exposurenotification.efgs.entity.InboundOperation;
import fi.thl.covid19.exposurenotification.efgs.util.CommonConst;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static fi.thl.covid19.exposurenotification.efgs.util.CommonConst.EfgsOperationState.FINISHED;
import static fi.thl.covid19.exposurenotification.efgs.util.CommonConst.EfgsOperationState.STARTED;
import static fi.thl.covid19.exposurenotification.efgs.util.CommonConst.EfgsOperationState.ERROR;
import static fi.thl.covid19.exposurenotification.efgs.util.CommonConst.MAX_RETRY_COUNT;
import static fi.thl.covid19.exposurenotification.efgs.util.CommonConst.STALLED_MIN_AGE_IN_MINUTES;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.Objects.requireNonNull;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

@Repository
public class InboundOperationDao {

    private static final Logger LOG = LoggerFactory.getLogger(InboundOperationDao.class);
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public InboundOperationDao(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = requireNonNull(jdbcTemplate);
    }

    @Transactional
    public Optional<Long> startInboundOperation(Optional<String> batchTag, LocalDate batchDate) {
        if (batchTag.isPresent()) {
            return startInboundOperation(Timestamp.from(Instant.now()), batchTag.get(), batchDate);
        } else {
            return startInboundOperation(Timestamp.from(Instant.now()), batchDate);
        }
    }

    @Transactional
    public Optional<Long> startInboundOperation(Timestamp timestamp, LocalDate batchDate) {
        String createOperation = "insert into en.efgs_inbound_operation (state, updated_at, batch_date) " +
                "select cast(:state as en.state_t), :updated_at, :batch_date " +
                "where not exists ( " +
                "select 1 from en.efgs_inbound_operation where " +
                "updated_at >= current_date::timestamp " +
                "and state <> cast(:error_state as en.state_t) " +
                ") " +
                "returning id";
        return jdbcTemplate.query(createOperation,
                Map.of(
                        "state", STARTED.name(),
                        "error_state", ERROR.name(),
                        "updated_at", timestamp,
                        "batch_date", Timestamp.from(batchDate.atStartOfDay(ZoneOffset.UTC).toInstant())
                ),
                (rs, i) -> rs.getLong(1))
                .stream().findFirst();
    }

    @Transactional
    public Optional<Long> startInboundOperation(Timestamp timestamp, String batchTag, LocalDate batchDate) {
        String createOperation = "insert into en.efgs_inbound_operation (state, updated_at, batch_tag, batch_date) " +
                "select cast(:state as en.state_t), :updated_at, :batch_tag, :batch_date " +
                "where not exists ( " +
                "select 1 from en.efgs_inbound_operation where " +
                "batch_tag = :batch_tag and " +
                "batch_date = :batch_date and " +
                "updated_at >= current_date::timestamp " +
                "and state = cast(:finished_state as en.state_t) " +
                ") " +
                "returning id";
        return jdbcTemplate.query(createOperation,
                Map.of(
                        "state", STARTED.name(),
                        "finished_state", FINISHED.name(),
                        "error_state", ERROR.name(),
                        "updated_at", timestamp,
                        "batch_tag", batchTag,
                        "batch_date", Timestamp.from(batchDate.atStartOfDay(ZoneOffset.UTC).toInstant())
                ),
                (rs, i) -> rs.getLong(1))
                .stream().findFirst();
    }

    @Transactional
    public boolean finishOperation(long operationId, int keysCountTotal, int failedKeysCount, Optional<String> batchTag) {
        String sql = "update en.efgs_inbound_operation set state = cast(:new_state as en.state_t), batch_tag = :batch_tag, " +
                "keys_count_total = :keys_count_total, invalid_signature_count = :invalid_signature_count, " +
                "updated_at = :updated_at, retry_count = retry_count + 1 where id = :id";
        Map<String, Object> params = new HashMap<>();
        params.put("new_state", FINISHED.name());
        params.put("id", operationId);
        params.put("batch_tag", batchTag.orElse(null));
        params.put("keys_count_total", keysCountTotal);
        params.put("invalid_signature_count", failedKeysCount);
        params.put("updated_at", Timestamp.from(Instant.now()));
        return jdbcTemplate.update(sql, params) == 1;
    }

    @Transactional
    public void markErrorOperation(long operationId, Optional<String> batchTag) {
        markErrorOperation(operationId, batchTag, Timestamp.from(Instant.now()));
    }

    @Transactional
    public void markErrorOperation(long operationId, Optional<String> batchTag, Timestamp timestamp) {
        String sql = "update en.efgs_inbound_operation set " +
                "state = cast(:error_state as en.state_t), " +
                "updated_at = :updated_at, " +
                "batch_tag = :batch_tag, retry_count = retry_count + 1 " +
                "where id = :id";
        Map<String, Object> params = new HashMap<>();
        params.put("error_state", ERROR.name());
        params.put("updated_at", timestamp);
        params.put("batch_tag", batchTag.orElse(null));
        params.put("id", operationId);
        jdbcTemplate.update(sql, params);
        LOG.info("Efgs inbound sync failed. {} {}",
                keyValue("operationId", operationId),
                keyValue("batchTag", batchTag));
    }

    @Transactional
    public void resolveStarted() {
        String sql = "update en.efgs_inbound_operation set state = cast(:new_state as en.state_t) " +
                "where state = cast(:current_state as en.state_t) " +
                "and updated_at < :stalled_operation_limit";
        jdbcTemplate.update(sql, Map.of(
                "new_state", ERROR.name(),
                "current_state", STARTED.name(),
                "stalled_operation_limit",
                Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).minus(STALLED_MIN_AGE_IN_MINUTES, MINUTES))
        ));
    }

    @Transactional
    public List<InboundOperation> getInboundErrors(LocalDate date) {
        String sql = "update en.efgs_inbound_operation set state = cast(:started_state as en.state_t) " +
                "where state = cast(:error_state as en.state_t) and retry_count < :max_retries " +
                "and updated_at > :start and updated_at < :end and batch_tag is not null " +
                "returning *";

        return jdbcTemplate.query(sql,
                Map.of(
                        "error_state", ERROR.name(),
                        "started_state", STARTED.name(),
                        "max_retries", MAX_RETRY_COUNT,
                        "start", Timestamp.valueOf(date.atStartOfDay()),
                        "end", Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).minus(STALLED_MIN_AGE_IN_MINUTES, MINUTES))
                ),
                this::transform);
    }

    @Transactional
    public int getNumberOfErrorsForDay() {
        String sql = "select count(*) from en.efgs_inbound_operation where " +
                "updated_at >= :datetime and state = cast(:error_state as en.state_t)";
        return jdbcTemplate.query(
                sql,
                Map.of("error_state", ERROR.name(),
                        "datetime", Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).minus(1, DAYS))
                ),
                (rs, i) -> rs.getInt(1)).stream().findFirst().orElseThrow(() -> new IllegalStateException("Count returned nothing."));
    }

    @Transactional
    public int getInvalidSignatureCountForDay() {
        String sql = "select sum(invalid_signature_count) from en.efgs_inbound_operation where invalid_signature_count is not null " +
                "and updated_at >= :datetime";
        return jdbcTemplate.query(
                sql,
                Map.of("datetime", Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).minus(1, DAYS))),
                (rs, i) -> rs.getInt(1)).stream().findFirst().orElseThrow(() -> new IllegalStateException("Invalid signature count returned nothing."));
    }

    private InboundOperation transform(ResultSet rs, int rowNum) throws SQLException {
        return new InboundOperation(
                rs.getLong("id"),
                CommonConst.EfgsOperationState.valueOf(rs.getString("state")),
                rs.getInt("keys_count_total"),
                rs.getInt("invalid_signature_count"),
                rs.getString("batch_tag"),
                rs.getInt("retry_count"),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getTimestamp("batch_date").toInstant().atZone(ZoneOffset.UTC).toLocalDate()
        );
    }
}
