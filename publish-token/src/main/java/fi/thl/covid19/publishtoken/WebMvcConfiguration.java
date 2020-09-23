package fi.thl.covid19.publishtoken;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.thl.covid19.publishtoken.error.CorrelationIdInterceptor;
import fi.thl.covid19.publishtoken.generation.v1.PublishTokenGenerationRequestArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

    private final CorrelationIdInterceptor correlationIdInterceptor;
    private final ObjectMapper jacksonObjectMapper;

    public WebMvcConfiguration(CorrelationIdInterceptor correlationIdInterceptor,
                               ObjectMapper jacksonObjectMapper) {
        this.correlationIdInterceptor = correlationIdInterceptor;
        this.jacksonObjectMapper = jacksonObjectMapper;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(correlationIdInterceptor);
    }

    @Override
    public void addArgumentResolvers(
            List<HandlerMethodArgumentResolver> argumentResolvers) {
        argumentResolvers.add(new PublishTokenGenerationRequestArgumentResolver(jacksonObjectMapper));
    }
}
