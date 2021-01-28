package fi.thl.covid19.publishtoken;

import fi.thl.covid19.publishtoken.generation.v1.PublishToken;
import fi.thl.covid19.publishtoken.verification.v1.PublishTokenVerification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

@Repository
public class PublishTokenDao {

    private static final Logger LOG = LoggerFactory.getLogger(PublishTokenDao.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PublishTokenDao(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = requireNonNull(jdbcTemplate);
    }

    @Transactional
    public boolean storeToken(PublishToken token, LocalDate symptomsOnset, String originService, String originUser, Optional<Boolean> symptomsExist) {
        try {
            String sql = "insert into " +
                    "pt.publish_token(token, created_at, valid_through, symptoms_onset, origin_service, origin_user, symptoms_exist) " +
                    "values(:token, :created_at, :valid_through, :symptoms_onset, :origin_service, :origin_user, :symptoms_exist)";
            Map<String, Object> params = new HashMap<>();
            params.put("token", token.token);
            params.put("created_at", new Timestamp(token.createTime.toEpochMilli()));
            params.put("valid_through", new Timestamp(token.validThroughTime.toEpochMilli()));
            params.put("symptoms_onset", symptomsOnset);
            params.put("origin_service", originService);
            params.put("origin_user", originUser);
            params.put("symptoms_exists", symptomsExist.orElse(null));
            LOG.info("Adding new publish token");
            addTokenCreateStatsRow(token.createTime, symptomsExist);
            return jdbcTemplate.update(sql, params) == 1;
        } catch (DuplicateKeyException e) {
            LOG.warn("Random token collision: {} {}", keyValue("service", originService), keyValue("user", originUser));
            return false;
        }
    }

    public List<PublishToken> getTokens(String originService, String originUser) {
        String sql =
                "select token, created_at, valid_through " +
                        "from pt.publish_token " +
                        "where origin_service = :origin_service and origin_user = :origin_user";
        Map<String, Object> params = Map.of("origin_service", originService, "origin_user", originUser);
        return jdbcTemplate.query(sql, params, this::mapToken);
    }

    public Optional<PublishTokenVerification> getVerification(String token) {
        String sql =
                "select id, symptoms_onset, symptoms_exists " +
                        "from pt.publish_token " +
                        "where token = :token and now() <= valid_through";
        Map<String, Object> params = Map.of("token", token);
        return jdbcTemplate.query(sql, params, this::mapVerification).stream().findFirst();
    }

    @Transactional
    public void deleteTokensExpiredBefore(Instant expiryLimit) {
        String sql = "delete from pt.publish_token where valid_through < :expiry_limit";
        Map<String, Object> params = Map.of("expiry_limit", new Timestamp(expiryLimit.toEpochMilli()));
        int count = jdbcTemplate.update(sql, params);
        LOG.info("Publish tokens deleted: {} {}", keyValue("expiryLimit", expiryLimit.toString()), keyValue("count", count));
    }

    public void addTokenCreateStatsRow(Instant createTime, Optional<Boolean> symptomsExist) {
        String sql = "insert into pt.stats_token_create(created_at, symptoms_exists) values (:created_at, :symptoms_exist)";
        Map<String, Object> params = new HashMap<>();
        params.put("created_at", new Timestamp(createTime.toEpochMilli()));
        params.put("symptoms_exists", symptomsExist.orElse(null));

        jdbcTemplate.update(sql, params);
    }

    public void addSmsStatsRow(Instant createTime) {
        String sql = "insert into pt.stats_sms_send(sent_at) values (:sent_at)";
        Map<String, Object> params = Map.of(
                "sent_at", new Timestamp(createTime.toEpochMilli()));
        jdbcTemplate.update(sql, params);
    }

    private PublishToken mapToken(ResultSet rs, int i) throws SQLException {
        return new PublishToken(
                rs.getString("token"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("valid_through").toInstant());
    }

    private PublishTokenVerification mapVerification(ResultSet rs, int i) throws SQLException {
        return new PublishTokenVerification(
                rs.getInt("id"),
                rs.getObject("symptoms_onset", LocalDate.class),
                Optional.ofNullable((Boolean) rs.getObject("symptoms_exists"))
        );
    }
}
