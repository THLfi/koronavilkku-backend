package fi.thl.covid19.exposurenotification.efgs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

@RestController
@RequestMapping("/efgs")
public class CallbackController {

    private static final Logger LOG = LoggerFactory.getLogger(CallbackController.class);

    private final FederationGatewaySyncService federationGatewaySyncService;

    public CallbackController(FederationGatewaySyncService federationGatewaySyncService) {
        this.federationGatewaySyncService = requireNonNull(federationGatewaySyncService);
    }

    @GetMapping("/callback")
    public void triggerCallback(@RequestParam("batchTag") String batchTag, @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LOG.info("Import from efgs triggered by callback {} {}.", keyValue("batchTag", batchTag), keyValue("date", date.toString()));
        federationGatewaySyncService.startInbound(date, Optional.of(batchTag));
        LOG.info("Import from efgs triggered by callback finished.");
    }
}
