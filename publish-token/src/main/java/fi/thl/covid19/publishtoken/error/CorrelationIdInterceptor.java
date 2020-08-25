package fi.thl.covid19.publishtoken.error;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Random;

@Component
public class CorrelationIdInterceptor implements HandlerInterceptor {

    private static final String CORRELATION_ID_KEY_NAME = "correlationId";

    private static final Random RAND = new Random();

    public static String getOrCreateCorrelationId() {
        String correlationId = MDC.get(CORRELATION_ID_KEY_NAME);

        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = Integer.toString(RAND.nextInt(Integer.MAX_VALUE));
            MDC.put(CORRELATION_ID_KEY_NAME, correlationId);
        }

        return correlationId;
    }

    public static void clearCorrelationID() {
        MDC.remove(CORRELATION_ID_KEY_NAME);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        getOrCreateCorrelationId();
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        clearCorrelationID();
    }
}
