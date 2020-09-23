package fi.thl.covid19.publishtoken.generation.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.thl.covid19.publishtoken.PublishTokenService;
import fi.thl.covid19.publishtoken.sms.SmsService;
import fi.thl.covid19.publishtoken.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import static fi.thl.covid19.publishtoken.Validation.validateUserName;
import static java.util.Objects.requireNonNull;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

@RestController
@RequestMapping("/publish-token/v1")
public class PublishTokenGenerationController {

    private static final Logger LOG = LoggerFactory.getLogger(PublishTokenGenerationController.class);

    public static final String SERVICE_NAME_HEADER = "KV-Request-Service";
    public static final String VALIDATE_ONLY_HEADER = "X-Validate-Only";

    private final PublishTokenService publishTokenService;
    private final SmsService smsService;
    private final ObjectMapper jacksonObjectMapper;

    public PublishTokenGenerationController(PublishTokenService publishTokenService,
                                            SmsService smsService,
                                            ObjectMapper jacksonObjectMapper) {
        this.publishTokenService = requireNonNull(publishTokenService);
        this.smsService = requireNonNull(smsService);
        this.jacksonObjectMapper = jacksonObjectMapper;
    }

    @PostMapping
    public PublishToken generateToken(@RequestHeader(name = SERVICE_NAME_HEADER) String rawRequestService,
                                      @RequestHeader(name = VALIDATE_ONLY_HEADER, required = false) boolean validateOnlyHeader,
                                      @RequestBody String requestBody) {
        String requestService = Validation.validateServiceName(rawRequestService);
        PublishTokenGenerationRequest request = Validation.validateAndCreate(requestBody, validateOnlyHeader, jacksonObjectMapper);
        if (request.validateOnly) {
            LOG.debug("API Validation Test: Generate new publish token: {} {}",
                    keyValue("service", requestService), keyValue("user", request.requestUser));
            return publishTokenService.generate();
        } else {
            LOG.info("Generating new publish token: {} {} {}",
                    keyValue("service", requestService),
                    keyValue("user", request.requestUser),
                    keyValue("smsUsed", request.patientSmsNumber.isPresent()));
            PublishToken token = publishTokenService.generateAndStore(
                    request.symptomsOnset,
                    requestService,
                    request.requestUser);
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
}
