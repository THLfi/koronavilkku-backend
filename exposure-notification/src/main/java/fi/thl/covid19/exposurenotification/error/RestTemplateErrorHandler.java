package fi.thl.covid19.exposurenotification.error;

import org.springframework.stereotype.Component;
import org.springframework.web.client.DefaultResponseErrorHandler;

@Component
public class RestTemplateErrorHandler extends DefaultResponseErrorHandler {
    public RestTemplateErrorHandler() {
    }
}
