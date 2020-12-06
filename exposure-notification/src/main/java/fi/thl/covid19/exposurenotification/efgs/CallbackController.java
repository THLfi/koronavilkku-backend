package fi.thl.covid19.exposurenotification.efgs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;

import static java.util.Objects.requireNonNull;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

@RestController
@RequestMapping("/efgs")
public class CallbackController {

    private static final Logger LOG = LoggerFactory.getLogger(CallbackController.class);

    private final FederationGatewaySyncService federationGatewaySyncService;
    private final boolean callbackEnabled;

    public CallbackController(
            FederationGatewaySyncService federationGatewaySyncService,
            @Value("${covid19.federation-gateway.call-back.enabled}") boolean callbackEnabled
    ) {
        this.federationGatewaySyncService = requireNonNull(federationGatewaySyncService);
        this.callbackEnabled = callbackEnabled;
    }

    @GetMapping("/callback")
    public String triggerCallback(@RequestParam("batchTag") String batchTag,
                                  @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                  HttpServletResponse response
    ) {
        if (callbackEnabled) {
            LOG.info("Import from efgs triggered by callback {} {}.", keyValue("batchTag", batchTag), keyValue("date", date.toString()));
            federationGatewaySyncService.startInboundAsync(date, batchTag);
            response.setStatus(HttpStatus.ACCEPTED.value());
            return "Request added to queue.";
        } else {
            LOG.warn("Callback request received, but callback is inactive. {} {}.", keyValue("batchTag", batchTag), keyValue("date", date.toString()));
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            return "Service is currently disabled.";
        }
    }
}
