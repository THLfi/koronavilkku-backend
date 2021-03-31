package fi.thl.covid19.exposurenotification.batch;

import fi.thl.covid19.exposurenotification.diagnosiskey.DiagnosisKeyDao;
import fi.thl.covid19.exposurenotification.diagnosiskey.TemporaryExposureKey;
import fi.thl.covid19.exposurenotification.error.BatchNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.fromV2to24hourInterval;
import static java.util.Objects.requireNonNull;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

@Service
public class BatchFileService {

    private static final Logger LOG = LoggerFactory.getLogger(BatchFileService.class);

    private static final String PRIVATE_KEY_ENV_VARIABLE = "EN_SIGNING_PRIVATE_PKCS8";

    private final DiagnosisKeyDao dao;
    private final BatchFileStorage batchFileStorage;

    private final String region;
    private final SignatureConfig signatureConfig;
    private final PrivateKey signingKey;

    public BatchFileService(DiagnosisKeyDao dao,
                            SignatureConfig signatureConfig,
                            BatchFileStorage batchFileStorage,
                            @Value("${covid19.region}") String region,
                            @Value("${covid19.diagnosis.signature.randomize-key:false}") boolean randomizeKey) {
        this.dao = requireNonNull(dao, "DAO required");
        this.batchFileStorage = requireNonNull(batchFileStorage, "BatchFileStorage required");
        this.signatureConfig = requireNonNull(signatureConfig, "SignatureConfig required");
        this.region = requireNonNull(region, "Region required");
        if (randomizeKey) {
            KeyPair keyPair = Signing.randomKeyPair();
            String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
            LOG.info("Using randomized signing key for diagnosis batches: {} {}",
                    keyValue("keyVersion", signatureConfig.keyVersion),
                    keyValue("public", publicKey));
            this.signingKey = keyPair.getPrivate();
        } else {
            LOG.info("Using signing key provided through env: {}", keyValue("keyVersion", signatureConfig.keyVersion));
            this.signingKey = Signing.privateKey(System.getenv(PRIVATE_KEY_ENV_VARIABLE));
        }
    }

    public int cacheMissingBatchesBetween(int fromInterval, int untilInterval) {
        int added = 0;
        List<Integer> available = dao.getAvailableIntervalsDirect();
        for (int interval = fromInterval; interval <= untilInterval; interval++) {
            BatchId id = new BatchId(interval);
            if (available.contains(interval) && !batchFileStorage.fileExists(id)) {
                batchFileStorage.addBatchFile(id, createBatchData(id));
                added++;
            }
        }
        return added;
    }

    public int cacheMissingBatchesBetweenV2(int fromInterval, int untilInterval) {
        int added = 0;
        List<Integer> available = dao.getAvailableIntervalsDirectV2();
        for (int interval = fromInterval; interval <= untilInterval; interval++) {
            BatchId id = new BatchId(fromV2to24hourInterval(interval), Optional.of(interval));
            if (available.contains(interval) && !batchFileStorage.fileExists(id)) {
                batchFileStorage.addBatchFile(id, createBatchDataV2(id));
                added++;
            }
        }
        return added;
    }

    public List<BatchId> listBatchIdsSince(BatchId previous, BatchIntervals intervals) {
        Stream<BatchId> batches = dao.getAvailableIntervals().stream()
                .filter(i -> i != intervals.current && intervals.isDistributed(i))
                .map(BatchId::new);
        if (intervals.current == intervals.last) {
            batches = Stream.concat(batches, getDemoBatchId(intervals.current).stream());
        }
        return batches.filter(previous::isBefore).collect(Collectors.toList());
    }

    public List<BatchId> listBatchIdsSinceV2(BatchId previous, BatchIntervals intervals) {
        Stream<BatchId> batches = dao.getAvailableIntervalsV2().stream()
                .filter(i -> i != intervals.current && intervals.isDistributed(i))
                .map(i -> new BatchId(fromV2to24hourInterval(i), Optional.of(i)));
        if (intervals.current == intervals.last) {
            batches = Stream.concat(batches, getDemoBatchIdV2(intervals.current).stream());
        }
        return batches.filter(previous::isBefore).collect(Collectors.toList());
    }

    public Optional<BatchId> getDemoBatchId(int currentInterval) {
        int count = dao.getKeyCount(currentInterval);
        return count > 0 ? Optional.of(new BatchId(currentInterval, Optional.of(Integer.parseInt("" + currentInterval + count)))) : Optional.empty();
    }

    public Optional<BatchId> getDemoBatchIdV2(int currentInterval) {
        int count = dao.getKeyCountV2(currentInterval);
        return count > 0 ? Optional.of(new BatchId(fromV2to24hourInterval(currentInterval), Optional.of(Integer.parseInt("" + currentInterval + count)))) : Optional.empty();
    }

    public BatchFile getBatchFile(BatchId id) {
        return batchFileStorage
                .readBatchFile(id)
                .map(data -> new BatchFile(id, data))
                .orElseGet(() -> {
                    LOG.warn("Batch file was not cached - generating it on the fly. This should not happen in production mode.");
                    return createBatchFile(id);
                });
    }

    public BatchFile createBatchFile(BatchId id) {
        return new BatchFile(id, createBatchData(id));
    }

    public BatchId getLatestBatchId(BatchIntervals intervals) {
        if (intervals.current == intervals.last) {
            return new BatchId(intervals.last, Optional.of(dao.getKeyCount(intervals.last)));
        } else {
            return new BatchId(intervals.last);
        }
    }

    public BatchId getLatestBatchIdV2(BatchIntervals intervals) {
        if (intervals.current == intervals.last) {
            return new BatchId(fromV2to24hourInterval(intervals.last), Optional.of(Integer.parseInt("" + intervals.last + dao.getKeyCountV2(intervals.last))));
        } else {
            return new BatchId(fromV2to24hourInterval(intervals.last), Optional.of(intervals.last));
        }
    }

    private byte[] createBatchData(BatchId id) {
        LOG.debug("Generating batch file: {}", keyValue("batchId", id));
        List<TemporaryExposureKey> keys = dao.getIntervalKeys(id.intervalNumber);
        if (keys.isEmpty()) {
            throw new BatchNotFoundException(id);
        } else {
            BatchMetadata metadata = BatchMetadata.of(id.intervalNumber, region);
            return BatchFileFactory.createBatchFile(signatureConfig, signingKey, metadata, keys);
        }
    }

    private byte[] createBatchDataV2(BatchId id) {
        LOG.debug("Generating V2 batch file: {}", keyValue("batchId", id));
        int intervalV2 = id.intervalNumberV2.orElseThrow();
        List<TemporaryExposureKey> keys = dao.getIntervalKeysV2(intervalV2);
        if (keys.isEmpty()) {
            throw new BatchNotFoundException(id);
        } else {
            BatchMetadata metadata = BatchMetadata.ofV2(intervalV2, region);
            return BatchFileFactory.createBatchFile(signatureConfig, signingKey, metadata, keys);
        }
    }
}
