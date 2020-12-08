package fi.thl.covid19.exposurenotification.efgs;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static fi.thl.covid19.exposurenotification.efgs.OperationDao.EfgsOperationDirection.INBOUND;
import static fi.thl.covid19.exposurenotification.efgs.OperationDao.EfgsOperationDirection.OUTBOUND;

@Configuration
public class IntegrationErrorMeterConfiguration {

    @Bean
    MeterBinder inboundErrorsLastDay(OperationDao operationDao) {
        return (registry) -> Gauge.builder("inbound_errors_last_24h", () -> operationDao.getNumberOfErrorsForDay(INBOUND)).register(registry);
    }

    @Bean
    MeterBinder outboundErrorsLastDay(OperationDao operationDao) {
        return (registry) -> Gauge.builder("outbound_errors_last_24h", () -> operationDao.getNumberOfErrorsForDay(OUTBOUND)).register(registry);
    }

    @Bean
    MeterBinder invalidSignatureCountForDay(OperationDao operationDao) {
        return (registry) -> Gauge.builder("invalid_signature_count_last_24h", operationDao::getInvalidSignatureCountForDay).register(registry);
    }
}
