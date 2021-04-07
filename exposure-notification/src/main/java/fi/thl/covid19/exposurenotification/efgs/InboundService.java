package fi.thl.covid19.exposurenotification.efgs;

import fi.thl.covid19.exposurenotification.diagnosiskey.DiagnosisKeyDao;
import fi.thl.covid19.exposurenotification.diagnosiskey.TemporaryExposureKey;
import fi.thl.covid19.exposurenotification.efgs.dao.InboundOperationDao;
import fi.thl.covid19.exposurenotification.efgs.entity.DownloadData;
import fi.thl.covid19.exposurenotification.efgs.signing.FederationGatewaySigning;
import fi.thl.covid19.proto.EfgsProto;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.to24HourInterval;
import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.toV2Interval;
import static fi.thl.covid19.exposurenotification.efgs.util.BatchUtil.*;
import static fi.thl.covid19.exposurenotification.efgs.util.SignatureValidationUtil.validateSignature;
import static java.util.Objects.requireNonNull;

@Service
public class InboundService {

    private static final Logger LOG = LoggerFactory.getLogger(InboundService.class);

    private final FederationGatewayClient client;
    private final DiagnosisKeyDao diagnosisKeyDao;
    private final InboundOperationDao inboundOperationDao;
    private final FederationGatewaySigning signer;
    private final MeterRegistry meterRegistry;

    private final String efgsTotalOperationsInbound = "efgs_total_operations_inbound";
    private final String efgsErrorOperationsInbound = "efgs_error_operations_inbound";
    private final String efgsVerificationTotal = "efgs_verification_total";
    private final String efgsVerificationFailed = "efgs_verification_failed";
    private final String efgsValidationFailed = "efgs_validation_failed";

    public InboundService(
            FederationGatewayClient client,
            DiagnosisKeyDao diagnosisKeyDao,
            InboundOperationDao inboundOperationDao,
            FederationGatewaySigning signer,
            MeterRegistry meterRegistry
    ) {
        this.client = requireNonNull(client);
        this.diagnosisKeyDao = requireNonNull(diagnosisKeyDao);
        this.inboundOperationDao = requireNonNull(inboundOperationDao);
        this.signer = requireNonNull(signer);
        this.meterRegistry = requireNonNull(meterRegistry);
        initCounters();
    }

    public void resolveCrash() {
        inboundOperationDao.resolveStarted();
    }

    @Async("callbackAsyncExecutor")
    public void startInboundAsync(LocalDate date, String batchTag) {
        MDC.clear();
        MDC.put("callbackBatchTag", batchTag);
        Optional<String> batchTagO = Optional.of(batchTag);
        Optional<Long> operationId = inboundOperationDao.startInboundOperation(batchTagO, date);
        operationId.flatMap(id -> downloadAndStore(id, batchTagO, getDateString(date)));
    }

    public void startInbound(LocalDate date, Optional<String> batchTag) {
        AtomicReference<Optional<String>> next = new AtomicReference<>(batchTag);
        String dateS = getDateString(date);
        do {
            MDC.put("scheduledInboundBatchTag", next.get().orElse(getBatchTag(date, "1")));
            Optional<Long> operationId = inboundOperationDao.startInboundOperation(next.get(), date);
            operationId.ifPresentOrElse(
                    id -> next.set(downloadAndStore(id, next.get(), dateS)),
                    () -> next.set(client.download(dateS, next.get()).flatMap(d -> d.nextBatchTag))
            );
        } while (next.get().isPresent());
    }

    public void startInboundRetry(LocalDate date) {
        inboundOperationDao.getInboundErrors(date).forEach(
                operation -> {
                    MDC.put("inboundRetryBatchTag", operation.batchTag.orElse(""));
                    downloadAndStore(
                            operation.id,
                            operation.batchTag,
                            getDateString(operation.batchDate)
                    );
                }
        );
    }

    private Optional<String> downloadAndStore(long operationId, Optional<String> batchTag, String date) {
        meterRegistry.counter(efgsTotalOperationsInbound).increment(1.0);
        AtomicBoolean finished = new AtomicBoolean(false);
        AtomicReference<Optional<String>> localBatchTag = new AtomicReference<>(batchTag);
        try {
            Optional<DownloadData> downloadO = client.download(date, localBatchTag.get());
            return downloadO.flatMap(download -> {
                localBatchTag.set(Optional.of(download.batchTag));
                EfgsProto.DiagnosisKeyBatch validBatch = validateSignature(
                        client.fetchAuditEntries(date, download.batchTag),
                        download,
                        signer.getTrustAnchor()
                );
                Instant now = Instant.now();
                int currentInterval = to24HourInterval(now);
                int currentIntervalV2 = toV2Interval(now);
                List<TemporaryExposureKey> successKeys = transform(validBatch, currentInterval, currentIntervalV2);
                diagnosisKeyDao.addInboundKeys(successKeys, currentInterval, currentIntervalV2);
                int signatureFailedCount = download.keysCount() - validBatch.getKeysCount();
                int validationFailedCount = validBatch.getKeysCount() - successKeys.size();
                meterRegistry.counter(efgsVerificationTotal).increment(download.keysCount());
                meterRegistry.counter(efgsVerificationFailed).increment(signatureFailedCount);
                meterRegistry.counter(efgsValidationFailed).increment(validationFailedCount);
                finished.set(
                        inboundOperationDao.finishOperation(
                                operationId,
                                download.keysCount(),
                                successKeys.size(),
                                validationFailedCount,
                                signatureFailedCount,
                                localBatchTag.get()
                        )
                );
                return download.nextBatchTag;
            });
        } finally {
            if (!finished.get()) {
                meterRegistry.counter(efgsErrorOperationsInbound).increment(1.0);
                inboundOperationDao.markErrorOperation(operationId, localBatchTag.get());
            }
        }
    }

    private void initCounters() {
        meterRegistry.counter(efgsTotalOperationsInbound);
        meterRegistry.counter(efgsErrorOperationsInbound);
        meterRegistry.counter(efgsVerificationTotal);
        meterRegistry.counter(efgsVerificationFailed);
        meterRegistry.counter(efgsValidationFailed);
    }
}
