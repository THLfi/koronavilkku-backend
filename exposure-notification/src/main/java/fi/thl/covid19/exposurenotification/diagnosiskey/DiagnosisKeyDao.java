package fi.thl.covid19.exposurenotification.diagnosiskey;

import fi.thl.covid19.exposurenotification.efgs.entity.OutboundOperation;
import fi.thl.covid19.exposurenotification.efgs.dao.OutboundOperationDao;
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
import java.util.*;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.from24hourToV2Interval;
import static fi.thl.covid19.exposurenotification.efgs.util.DummyKeyGeneratorUtil.*;
import static fi.thl.covid19.exposurenotification.efgs.util.CommonConst.MAX_RETRY_COUNT;
import static java.util.Objects.requireNonNull;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

@Repository
public class DiagnosisKeyDao {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosisKeyDao.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final OutboundOperationDao outboundOperationDao;

    public DiagnosisKeyDao(NamedParameterJdbcTemplate jdbcTemplate,
                           OutboundOperationDao outboundOperationDao) {
        this.jdbcTemplate = requireNonNull(jdbcTemplate);
        this.outboundOperationDao = requireNonNull(outboundOperationDao);

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
    public void addKeys(int verificationId, String requestChecksum, int interval, int intervalV2, List<TemporaryExposureKey> keys, long exportedKeyCount) {
        if (verify(verificationId, requestChecksum, keys.size(), exportedKeyCount) && !keys.isEmpty()) {
            batchInsert(keys, Optional.empty());
            LOG.info("Inserted keys: {} {} {}", keyValue("interval", interval), keyValue("intervalV2", intervalV2), keyValue("count", keys.size()));
        }
    }

    @Cacheable(value = "available-intervals", sync = true)
    public List<Integer> getAvailableIntervals() {
        return getAvailableIntervalsDirect();
    }

    @Cacheable(value = "available-intervals-v2", sync = true)
    public List<Integer> getAvailableIntervalsV2() {
        return getAvailableIntervalsDirectV2();
    }

    @Transactional
    public List<Integer> getAvailableIntervalsDirect() {
        LOG.info("Fetching available intervals");
        String sql_v1 = "select distinct submission_interval from en.diagnosis_key order by submission_interval";
        return jdbcTemplate.query(sql_v1, (rs, i) -> rs.getInt("submission_interval"));
    }

    @Transactional
    public List<Integer> getAvailableIntervalsDirectV2() {
        LOG.info("Fetching available intervals for V2");
        String sql = "select distinct submission_interval_v2 from en.diagnosis_key order by submission_interval_v2";
        return jdbcTemplate.query(sql, (rs, i) -> rs.getInt("submission_interval_v2"));
    }

    @Cacheable(value = "key-count", sync = true)
    public int getKeyCount(int interval) {
        LOG.info("Fetching key-count from DB: {}", keyValue("interval", interval));
        String sql = "select count(*) from en.diagnosis_key where submission_interval = :interval";
        Map<String, Object> params = Map.of("interval", interval);
        return jdbcTemplate.query(sql, params, (rs, i) -> rs.getInt(1))
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("Count returned nothing."));
    }

    @Cacheable(value = "key-count-v2", sync = true)
    public int getKeyCountV2(int intervalV2) {
        LOG.info("Fetching key-count from DB: {}", keyValue("intervalV2", intervalV2));
        String sql = "select count(*) from en.diagnosis_key where submission_interval_v2 = :interval_v2";
        Map<String, Object> params = Map.of("interval_v2", intervalV2);
        return jdbcTemplate.query(sql, params, (rs, i) -> rs.getInt(1))
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("Count returned nothing."));
    }

    @Transactional
    public List<TemporaryExposureKey> getIntervalKeys(int interval) {
        LOG.info("Fetching keys: {}", keyValue("interval", interval));
        String sql = "select key_data, rolling_period, rolling_start_interval_number, transmission_risk_level, " +
                "submission_interval, submission_interval_v2, " +
                "origin, visited_countries, days_since_onset_of_symptoms, consent_to_share, symptoms_exist " +
                "from en.diagnosis_key " +
                "where submission_interval = :interval " +
                // Level 0 & 7 would get 0 score anyhow, so ignore them
                // This also clips the range, so that we can manage the difference between iOS & Android APIs
                "and transmission_risk_level between 1 and 6 " +
                "order by key_data";
        Map<String, Object> params = Map.of("interval", interval);
        // We should not have invalid data in the DB, but if we do, pass by it and move on
        List<TemporaryExposureKey> keys = jdbcTemplate.query(sql, params, (rs, i) -> mapValidKey(interval, rs, i))
                .stream().flatMap(Optional::stream).collect(Collectors.toList());

        if (keys.isEmpty() || keys.size() >= BATCH_MIN_SIZE) {
            return keys;
        } else {
            List<TemporaryExposureKey> dummyKeys = generateDummyKeys(BATCH_MIN_SIZE - keys.size(), false, from24hourToV2Interval(interval), Instant.now());
            batchInsert(dummyKeys, Optional.empty());
            return concatDummyKeys(keys, dummyKeys);
        }
    }

    @Transactional
    public List<TemporaryExposureKey> getIntervalKeysV2(int intervalV2) {
        LOG.info("Fetching keys: {}", keyValue("intervalV2", intervalV2));
        String sql = "select key_data, rolling_period, rolling_start_interval_number, transmission_risk_level, " +
                "submission_interval, submission_interval_v2, " +
                "origin, visited_countries, days_since_onset_of_symptoms, consent_to_share, symptoms_exist " +
                "from en.diagnosis_key " +
                "where submission_interval_v2 = :interval_v2 " +
                // Level 0 & 7 would get 0 score anyhow, so ignore them
                // This also clips the range, so that we can manage the difference between iOS & Android APIs
                "and transmission_risk_level between 1 and 6 " +
                "order by key_data";
        Map<String, Object> params = Map.of("interval_v2", intervalV2);
        // We should not have invalid data in the DB, but if we do, pass by it and move on
        List<TemporaryExposureKey> keys = jdbcTemplate.query(sql, params, (rs, i) -> mapValidKey(intervalV2, rs, i))
                .stream().flatMap(Optional::stream).collect(Collectors.toList());

        if (keys.isEmpty() || keys.size() >= BATCH_MIN_SIZE) {
            return keys;
        } else {
            List<TemporaryExposureKey> dummyKeys = generateDummyKeys(BATCH_MIN_SIZE - keys.size(), false, intervalV2, Instant.now());
            batchInsert(dummyKeys, Optional.empty());
            return concatDummyKeys(keys, dummyKeys);
        }
    }

    @Transactional
    public boolean verify(int verificationId, String requestChecksum, long totalKeyCount, long exportedKeyCount) {
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
            addReportKeysStatsRow(Instant.now(), totalKeyCount, exportedKeyCount);
            return true;
        } else if (requestChecksum.equals(getVerifiedChecksum(verificationId))) {
            return false;
        } else {
            throw new TokenValidationException();
        }
    }

    @Transactional
    public Optional<OutboundOperation> fetchAvailableKeysForEfgs(boolean retry) {
        LOG.info("Fetching queued keys not sent to efgs.");
        Timestamp timestamp = Timestamp.from(Instant.now());
        String sql = "with batch as ( " +
                "select key_data " +
                "from en.diagnosis_key " +
                "where efgs_sync is null and retry_count >= :min_retry_count and retry_count < :max_retry_count " +
                "and consent_to_share " +
                "order by key_data for update skip locked limit 5000 ) " +
                "update en.diagnosis_key " +
                "set efgs_sync = :timestamp, retry_count = retry_count + 1 " +
                "where key_data in (select key_data from batch) " +
                "returning key_data, rolling_period, rolling_start_interval_number, transmission_risk_level, " +
                "visited_countries, days_since_onset_of_symptoms, origin, consent_to_share, symptoms_exist, " +
                "submission_interval, submission_interval_v2";

        List<TemporaryExposureKey> keys = new ArrayList<>(jdbcTemplate.query(sql, Map.of(
                "min_retry_count", retry ? 1 : 0,
                "max_retry_count", retry ? MAX_RETRY_COUNT : 1,
                "timestamp", timestamp
        ), (rs, i) -> mapKey(rs)));

        if (keys.isEmpty()) {
            return Optional.empty();
        } else if (!retry && keys.size() < BATCH_MIN_SIZE) {
            List<TemporaryExposureKey> dummyKeys = generateDummyKeys(BATCH_MIN_SIZE - keys.size(), true);
            batchInsert(dummyKeys, Optional.of(timestamp));
            List<TemporaryExposureKey> concatKeys = concatDummyKeys(keys, dummyKeys);
            return constructOutboundOperation(concatKeys, timestamp);
        } else {
            return constructOutboundOperation(keys, timestamp);
        }
    }

    private Optional<OutboundOperation> constructOutboundOperation(List<TemporaryExposureKey> keys, Timestamp timestamp) {
        return Optional.of(
                new OutboundOperation(
                        keys,
                        outboundOperationDao.startOutboundOperation(timestamp)));
    }

    @Transactional
    public void setNotSent(OutboundOperation operation) {
        String sql = "update en.diagnosis_key set efgs_sync = null where key_data = :key_data";
        jdbcTemplate.batchUpdate(sql, operation.keys.stream().map(key -> Map.of("key_data", key.keyData))
                .toArray((IntFunction<Map<String, String>[]>) Map[]::new)
        );

        outboundOperationDao.markErrorOperation(operation.operationId, Optional.of(operation.batchTag));
    }

    @Transactional
    public void resolveOutboundCrash() {
        List<Timestamp> crashed = outboundOperationDao.getAndResolveStarted();

        if (!crashed.isEmpty()) {
            String sql = "update en.diagnosis_key set efgs_sync = null, retry_count = 0 where efgs_sync in (:timestamp)";
            jdbcTemplate.update(sql, Map.of("timestamp", crashed));
        }
    }

    @Transactional
    public void addInboundKeys(List<TemporaryExposureKey> keys, int interval, int intervalV2) {
        if (!keys.isEmpty()) {
            batchInsert(keys, Optional.of(new Timestamp(Instant.now().toEpochMilli())));
            LOG.info("Inserted keys from efgs: {} {} {}",
                    keyValue("interval", interval), keyValue("intervalV2", intervalV2), keyValue("count", keys.size()));
        }
    }

    private String getVerifiedChecksum(int verificationId) {
        String sql = "select request_checksum from en.token_verification where verification_id=:verification_id";
        return jdbcTemplate.queryForObject(sql, Map.of("verification_id", verificationId), String.class);
    }

    private void batchInsert(List<TemporaryExposureKey> newKeys, Optional<Timestamp> efgsSync) {
        String sql = "insert into " +
                "en.diagnosis_key (key_data, rolling_period, rolling_start_interval_number, transmission_risk_level, " +
                "submission_interval, submission_interval_v2, origin, visited_countries, days_since_onset_of_symptoms, consent_to_share, efgs_sync, symptoms_exist) " +
                "values (:key_data, :rolling_period, :rolling_start_interval_number, :transmission_risk_level, " +
                ":submission_interval, :submission_interval_v2, :origin, :visited_countries, :days_since_onset_of_symptoms, :consent_to_share, :efgs_sync, :symptoms_exist) " +
                "on conflict do nothing";
        Map<String, Object>[] params = newKeys.stream()
                .map(key -> createParamsMap(key, efgsSync))
                .toArray((IntFunction<Map<String, Object>[]>) Map[]::new);
        jdbcTemplate.batchUpdate(sql, params);
    }

    private void addReportKeysStatsRow(Instant createTime, long totalKeyCount, long exportedKeyCount) {
        String sql = "insert into en.stats_report_keys(reported_at, total_key_count, exported_key_count) " +
                "values (:reported_at, :total_key_count, :exported_key_count)";
        Map<String, Object> params = Map.of(
                "reported_at", new Timestamp(createTime.toEpochMilli()),
                "total_key_count", totalKeyCount,
                "exported_key_count", exportedKeyCount);
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
                rs.getInt("rolling_period"),
                Set.of((String[]) rs.getArray("visited_countries").getArray()),
                Optional.ofNullable((Integer) rs.getObject("days_since_onset_of_symptoms")),
                rs.getString("origin"),
                rs.getBoolean("consent_to_share"),
                Optional.ofNullable((Boolean) rs.getObject("symptoms_exist")),
                rs.getInt("submission_interval"),
                rs.getInt("submission_interval_v2")
        );
    }

    private Map<String, Object> createParamsMap(TemporaryExposureKey key, Optional<Timestamp> efgsSync) {
        Map<String, Object> params = new HashMap<>();
        params.put("key_data", key.keyData);
        params.put("rolling_period", key.rollingPeriod);
        params.put("rolling_start_interval_number", key.rollingStartIntervalNumber);
        params.put("transmission_risk_level", key.transmissionRiskLevel);
        params.put("submission_interval", key.submissionInterval);
        params.put("submission_interval_v2", key.submissionIntervalV2);
        params.put("origin", key.origin);
        params.put("visited_countries", key.visitedCountries.toArray(new String[0]));
        params.put("consent_to_share", key.consentToShareWithEfgs);
        params.put("days_since_onset_of_symptoms", key.daysSinceOnsetOfSymptoms.orElse(null));
        params.put("efgs_sync", efgsSync.orElse(null));
        params.put("symptoms_exist", key.symptomsExist.orElse(null));
        return params;
    }
}
