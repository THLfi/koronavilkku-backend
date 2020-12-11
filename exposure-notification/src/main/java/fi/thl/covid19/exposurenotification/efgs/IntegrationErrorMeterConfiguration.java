package fi.thl.covid19.exposurenotification.efgs;

import fi.thl.covid19.exposurenotification.efgs.dao.InboundOperationDao;
import fi.thl.covid19.exposurenotification.efgs.dao.OutboundOperationDao;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class IntegrationErrorMeterConfiguration {

    @Bean
    MeterBinder inboundErrorsLastDay(InboundOperationDao inboundOperationDao) {
        return (registry) -> Gauge.builder("efgs_inbound_errors_last_24h", inboundOperationDao::getNumberOfErrorsForDay).register(registry);
    }

    @Bean
    MeterBinder outboundErrorsLastDay(OutboundOperationDao outboundOperationDao) {
        return (registry) -> Gauge.builder("efgs_outbound_errors_last_24h", outboundOperationDao::getNumberOfErrorsForDay).register(registry);
    }

    @Bean
    MeterBinder invalidSignatureCountForDay(InboundOperationDao inboundOperationDao) {
        return (registry) -> Gauge.builder("efgs_invalid_signature_count_last_24h", inboundOperationDao::getInvalidSignatureCountForDay).register(registry);
    }
}
