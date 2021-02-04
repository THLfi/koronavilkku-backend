package fi.thl.covid19.publishtoken.generation.v1;

import fi.thl.covid19.publishtoken.PublishTokenService;
import fi.thl.covid19.publishtoken.Validation;
import fi.thl.covid19.publishtoken.sms.SmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

import static fi.thl.covid19.publishtoken.Validation.validateUserName;
import static java.util.Objects.requireNonNull;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

@RestController
@RequestMapping("/publish-token/v1")
public class PublishTokenGenerationController {

    private static final Logger LOG = LoggerFactory.getLogger(PublishTokenGenerationController.class);

    public static final String SERVICE_NAME_HEADER = "KV-Request-Service";
    public static final String VALIDATE_ONLY_HEADER = "KV-Validate-Only";

    private final PublishTokenService publishTokenService;
    private final SmsService smsService;

    public PublishTokenGenerationController(PublishTokenService publishTokenService, SmsService smsService) {
        this.publishTokenService = requireNonNull(publishTokenService);
        this.smsService = requireNonNull(smsService);
    }

    @PostMapping
    public PublishToken generateToken(@RequestHeader(name = SERVICE_NAME_HEADER) String rawRequestService,
                                      @RequestHeader(name = VALIDATE_ONLY_HEADER, required = false) boolean validateOnly,
                                      @RequestBody PublishTokenGenerationRequest request) {
        String requestService = Validation.validateServiceName(rawRequestService);
        LocalDate correctedDate = fixForErroneousSymptomsOnsetSendByClient(request.symptomsOnset, requestService);

        if (validateOnly || request.validateOnly) {
            LOG.debug("API Validation Test: Generate new publish token: {} {}",
                    keyValue("service", requestService), keyValue("user", request.requestUser));
            return publishTokenService.generate();
        } else {
            LOG.info("Generating new publish token: {} {} {}",
                    keyValue("service", requestService),
                    keyValue("user", request.requestUser),
                    keyValue("smsUsed", request.patientSmsNumber.isPresent()));
            PublishToken token = publishTokenService.generateAndStore(
                    correctedDate,
                    requestService,
                    request.requestUser,
                    request.symptomsExist);
            request.patientSmsNumber.ifPresent(number -> smsService.send(number, token));
            return token;
        }
    }

    @GetMapping("/{user}")
    public PublishTokenList getTokensBy(@RequestHeader(name = SERVICE_NAME_HEADER) String rawRequestService, @PathVariable(value = "user") String user) {
        String validatedService = Validation.validateServiceName(rawRequestService);
        String validatedUser = validateUserName(requireNonNull(user));
        LOG.info("Fetching tokens: {} {}", keyValue("service", validatedService), keyValue("user", user));
        return new PublishTokenList(publishTokenService.getTokensBy(validatedService, validatedUser));
    }

    // TODO: remove after fix is made on client side
    private LocalDate fixForErroneousSymptomsOnsetSendByClient(LocalDate receivedDate, String requestService) {
        if ("www.omaolo.fi".equals(requestService)) {
            return receivedDate.plusDays(1);
        } else {
            return receivedDate;
        }
    }
}
