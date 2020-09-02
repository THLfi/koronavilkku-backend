package fi.thl.covid19.publishtoken.error;

import io.micrometer.core.lang.Nullable;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Optional;

import static fi.thl.covid19.publishtoken.error.CorrelationIdInterceptor.clearCorrelationID;
import static fi.thl.covid19.publishtoken.error.CorrelationIdInterceptor.getOrCreateCorrelationId;
import static net.logstash.logback.argument.StructuredArguments.keyValue;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

/**
 * Specific client-side mistakes are identified as client errors, trying to describe back the reason.
 * Anything that slips past those, is an internal server error, resulting in a terse (no details) message back to client.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class ApiErrorHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler({InputValidationException.class})
    public ResponseEntity<Object> handleInputValidationError(InputValidationException ex, WebRequest request) {
        return handleExceptionInternal(ex, ex.getMessage(), new HttpHeaders(), BAD_REQUEST, request);
    }

    @ExceptionHandler({ClientAbortException.class})
    public ResponseEntity<Object> handleClientAbortException(ClientAbortException ex, WebRequest request) {
        String correlationId = getOrCreateCorrelationId();
        logHandled(ex.toString(), INTERNAL_SERVER_ERROR, request);
        return respondToError(correlationId, Optional.of("Client abort"), INTERNAL_SERVER_ERROR, new HttpHeaders());
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {
        Throwable cause = ex.getMostSpecificCause();
        String message = cause instanceof InputValidationException ? cause.getMessage() : "Invalid request content";
        return handleExceptionInternal(ex, message, headers, status, request);
    }

    @ExceptionHandler({Exception.class})
    public ResponseEntity<Object> handleAnyException(Exception ex, WebRequest request) {
        return handleExceptionInternal(ex, null, new HttpHeaders(), INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatus status, WebRequest request) {
        String errorId = getOrCreateCorrelationId();
        if (ex instanceof InputValidationException && ((InputValidationException) ex).validateOnly) {
            logHandledDebug(ex.toString(), status, request);
        } else if (status.is4xxClientError()) {
            logHandled(ex.toString(), status, request);
        } else {
            logUnhandled(ex, status, request);
        }
        return respondToError(errorId, Optional.ofNullable(body).map(Object::toString), status, headers);
    }

    private void logHandled(String ex, HttpStatus status, WebRequest request) {
        LOG.warn("Error processing request: {} {} {} {}",
                keyValue("code", status.value()),
                keyValue("status", status.getReasonPhrase()),
                keyValue("request", request.getDescription(false)),
                keyValue("exception", ex));
    }

    private void logUnhandled(Exception ex, HttpStatus status, WebRequest request) {
        LOG.error("Unexpected error: {} {} {}",
                keyValue("code", status.value()),
                keyValue("status", status.getReasonPhrase()),
                keyValue("request", request.getDescription(false)),
                ex);
    }

    private void logHandledDebug(String ex, HttpStatus status, WebRequest request) {
        LOG.debug("Error processing request: {} {} {} {}",
                keyValue("code", status.value()),
                keyValue("status", status.getReasonPhrase()),
                keyValue("request", request.getDescription(false)),
                keyValue("exception", ex));
    }

    private ResponseEntity<Object> respondToError(String errorId, Optional<String> message, HttpStatus status, HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        clearCorrelationID();
        return new ResponseEntity<>(new ApiError(errorId, status.value(), message), headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleMissingPathVariable(MissingPathVariableException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {
        String message = "Request path variable \"" + ex.getVariableName() + "\" missing";
        return this.handleExceptionInternal(ex, message, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {
        String message = "Request parameter \"" + ex.getParameterName() + "\" missing";
        return handleExceptionInternal(ex, message, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(ServletRequestBindingException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {
        return handleExceptionInternal(ex, "Request binding failed", headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {
        return handleExceptionInternal(ex, "Invalid request parameter", headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleBindException(BindException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {
        return handleExceptionInternal(ex, "Binding failed", headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(NoHandlerFoundException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {
        return handleExceptionInternal(ex, "Invalid request path", headers, status, request);
    }
}
