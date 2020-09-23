package fi.thl.covid19.publishtoken.generation.v1;

import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.thl.covid19.publishtoken.Validation;
import fi.thl.covid19.publishtoken.error.InputValidationException;
import fi.thl.covid19.publishtoken.error.InputValidationValidateOnlyException;
import org.springframework.core.MethodParameter;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

public class PublishTokenGenerationRequestArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String VALIDATE_ONLY_HEADER = "X-Validate-Only";

    private final ObjectMapper jacksonObjectMapper;

    public PublishTokenGenerationRequestArgumentResolver(ObjectMapper jacksonObjectMapper) {
        this.jacksonObjectMapper = jacksonObjectMapper;
    }

    @Override
    public boolean supportsParameter(MethodParameter methodParameter) {
        return methodParameter.getParameterAnnotation(PublishTokenGenerationData.class) != null;
    }

    @Override
    public Object resolveArgument(MethodParameter methodParameter,
                                  ModelAndViewContainer modelAndViewContainer,
                                  NativeWebRequest nativeWebRequest,
                                  WebDataBinderFactory webDataBinderFactory) throws Exception {
        boolean validateOnly = Validation.validateBooleanHeader(nativeWebRequest, VALIDATE_ONLY_HEADER);
        HttpServletRequest request = nativeWebRequest.getNativeRequest(HttpServletRequest.class);

        if (request != null) {
            return validateAndCreate(FileCopyUtils.copyToString(request.getReader()), validateOnly);
        } else {
            throw new NullPointerException("Request is null");
        }
    }

    private PublishTokenGenerationRequest validateAndCreate(String body, boolean validateOnly) {
        InjectableValues injectableValues = new InjectableValues.Std().addValue(boolean.class, validateOnly);
        try {
            return jacksonObjectMapper.reader(injectableValues).readValue(body, PublishTokenGenerationRequest.class);
        } catch (IOException e) {
            if (validateOnly) {
                throw new InputValidationValidateOnlyException("ValidateOnly: " + e.getMessage());
            } else {
                throw new InputValidationException(e.getMessage());
            }
        }
    }
}
