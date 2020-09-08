package fi.thl.covid19.exposurenotification.diagnosiskey;

import fi.thl.covid19.exposurenotification.diagnosiskey.v1.TemporaryExposureKey;
import fi.thl.covid19.exposurenotification.error.InputValidationException;
import fi.thl.covid19.exposurenotification.error.TokenValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

@Repository
public class DiagnosisKeyDao {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosisKeyDao.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DiagnosisKeyDao(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = requireNonNull(jdbcTemplate);
        LOG.info("Initialized");
    }

    @Transactional
    public int deleteKeysBefore(int interval) {
        String sql = "delete from en.diagnosis_key where submission_interval < :interval";
        int count = jdbcTemplate.update(sql, Map.of("interval", interval));
        LOG.info("Keys deleted: {} {}", keyValue("beforeInterval", interval), keyValue("count", count));
        return count;
    }

    @Transactional
    public int deleteVerificationsBefore(Instant verificationTime) {
        String sql = "delete from en.token_verification where verification_time < :verification_time";
        Map<String, Object> params = Map.of("verification_time", new Timestamp(verificationTime.toEpochMilli()));
        int count = jdbcTemplate.update(sql, params);
        LOG.info("Token verifications deleted: {} {}", keyValue("beforeVerificationTime", verificationTime.toString()), keyValue("count", count));
        return count;
    }

    @Transactional
    public void addKeys(int verificationId, String requestChecksum, int interval, List<TemporaryExposureKey> keys) {
        if (verify(verificationId, requestChecksum) && !keys.isEmpty()) {
            batchInsert(interval, keys);
            addReportKeysStatsRow(Instant.now());
            LOG.info("Inserted keys: {} {}", keyValue("interval", interval), keyValue("count", keys.size()));
        }
    }

    @Cacheable(value = "available-intervals", sync = true)
    public List<Integer> getAvailableIntervals() {
        return getAvailableIntervalsDirect();
    }

    public List<Integer> getAvailableIntervalsDirect() {
        LOG.info("Fetching available intervals");
        String sql = "select distinct submission_interval from en.diagnosis_key order by submission_interval";
        return jdbcTemplate.query(sql, (rs,i) -> rs.getInt("submission_interval"));
    }

    @Cacheable(value = "key-count", sync = true)
    public int getKeyCount(int interval) {
        LOG.info("Fetching key-count from DB: {}", keyValue("interval", interval));
        String sql = "select count(*) from en.diagnosis_key where submission_interval = :interval";
        Map<String,Object> params = Map.of("interval", interval);
        return jdbcTemplate.query(sql, params, (rs,i) -> rs.getInt(1))
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("Count returned nothing."));
    }

    public List<TemporaryExposureKey> getIntervalKeys(int interval) {
        LOG.info("Fetching keys: {}", keyValue("interval", interval));
        String sql =
                "select key_data, rolling_period, rolling_start_interval_number, transmission_risk_level, submission_interval " +
                "from en.diagnosis_key " +
                "where submission_interval = :interval " +
                // Level 0 & 7 would get 0 score anyhow, so ignore them
                // This also clips the range, so that we can manage the difference between iOS & Android APIs
                "and transmission_risk_level between 1 and 6 " +
                "order by key_data";
        Map<String, Object> params = Map.of("interval", interval);
        // We should not have invalid data in the DB, but if we do, pass by it and move on
        return jdbcTemplate.query(sql, params, (rs,i) -> mapValidKey(interval, rs, i))
                .stream().flatMap(Optional::stream).collect(Collectors.toList());
    }

    @Transactional
    public boolean verify(int verificationId, String requestChecksum) {
        String sql = "insert into " +
                "en.token_verification (verification_id, request_checksum) " +
                "values (:verification_id, :request_checksum) " +
                "on conflict do nothing";
        Map<String, Object> params = Map.of(
                "verification_id", verificationId,
                "request_checksum", requestChecksum);
        boolean rowCreated = jdbcTemplate.update(sql, new MapSqlParameterSource(params)) == 1;
        LOG.info("Marked token verification: {}", keyValue("newVerification", rowCreated));
        if (rowCreated) {
            return true;
        } else if (requestChecksum.equals(getVerifiedChecksum(verificationId))) {
            return false;
        } else {
            throw new TokenValidationException();
        }
    }

    private String getVerifiedChecksum(int verificationId) {
        String sql = "select request_checksum from en.token_verification where verification_id=:verification_id";
        return jdbcTemplate.queryForObject(sql, Map.of("verification_id", verificationId), String.class);
    }

    private void batchInsert(int interval, List<TemporaryExposureKey> newKeys) {
        String sql = "insert into " +
                "en.diagnosis_key (key_data, rolling_period, rolling_start_interval_number, transmission_risk_level, submission_interval) " +
                "values (:key_data, :rolling_period, :rolling_start_interval_number, :transmission_risk_level, :submission_interval) " +
                "on conflict do nothing";
        Map<String,Object>[] params = newKeys.stream()
                .map(key -> createParamsMap(interval, key))
                .toArray((IntFunction<Map<String, Object>[]>) Map[]::new);
        jdbcTemplate.batchUpdate(sql, params);
    }

    private void addReportKeysStatsRow(Instant createTime) {
        String sql = "insert into en.stats_report_keys(reported_at) values (:reported_at)";
        Map<String, Object> params = Map.of(
                "reported_at", new Timestamp(createTime.toEpochMilli()));
        jdbcTemplate.update(sql, params);
    }

    private Optional<TemporaryExposureKey> mapValidKey(int interval, ResultSet rs, int index) throws SQLException {
        try {
            return Optional.of(mapKey(rs));
        } catch (InputValidationException e) {
            LOG.error("Bad exposure keys in DB: {} {}", keyValue("interval", interval), keyValue("index", index), e);
            return Optional.empty();
        }
    }

    private TemporaryExposureKey mapKey(ResultSet rs) throws SQLException {
        return new TemporaryExposureKey(
                rs.getString("key_data"),
                rs.getInt("transmission_risk_level"),
                rs.getInt("rolling_start_interval_number"),
                rs.getInt("rolling_period"));
    }

    private Map<String,Object> createParamsMap(int interval, TemporaryExposureKey key) {
        return Map.of(
                "key_data", key.keyData,
                "rolling_period", key.rollingPeriod,
                "rolling_start_interval_number", key.rollingStartIntervalNumber,
                "transmission_risk_level", key.transmissionRiskLevel,
                "submission_interval", interval);
    }
}
