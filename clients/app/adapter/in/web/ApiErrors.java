package app.adapter.in.web;

import app.domain.cases.ActionNotAvailableException;
import app.domain.cases.ActiveCaseExistsException;
import app.domain.cases.CaseNotFoundException;
import app.domain.cases.InvalidActionException;
import app.domain.client.ClientNotDeletableException;
import app.domain.client.ClientNotFoundException;
import app.domain.client.ConcurrentClientUpdateException;
import app.domain.client.InvalidClientTransitionException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every error to an RFC 7807 {@code application/problem+json} response. Details are fixed,
 * safe texts: internal exception messages and stack traces never reach the client.
 */
@RestControllerAdvice
class ApiErrors extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

  @ExceptionHandler({ClientNotFoundException.class, CaseNotFoundException.class})
  ResponseEntity<Object> notFound(RuntimeException ex) {
    return problem(HttpStatus.NOT_FOUND, "Resource not found", Map.of());
  }

  /** Domain conflicts carry fixed, safe messages written by the domain itself. */
  @ExceptionHandler({
    InvalidClientTransitionException.class,
    ClientNotDeletableException.class,
    ConcurrentClientUpdateException.class,
    ActiveCaseExistsException.class,
    ActionNotAvailableException.class
  })
  ResponseEntity<Object> conflict(RuntimeException ex) {
    return problem(HttpStatus.CONFLICT, ex.getMessage(), Map.of());
  }

  @ExceptionHandler(InvalidActionException.class)
  ResponseEntity<Object> invalid(InvalidActionException ex) {
    return problem(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.errors());
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<Object> typeMismatch(MethodArgumentTypeMismatchException ex) {
    return problem(
        HttpStatus.BAD_REQUEST, "Invalid parameter", Map.of(ex.getName(), "has an invalid value"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> unexpected(Exception ex) {
    log.error("Unexpected error", ex);
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", Map.of());
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Map<String, String> errors = new TreeMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
    return problem(HttpStatus.BAD_REQUEST, "Invalid request content", errors);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return problem(HttpStatus.BAD_REQUEST, "Malformed JSON request", Map.of());
  }

  /** Normalises the problems built by Spring MVC itself (405, 415, ...) to the same safe shape. */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    var status = HttpStatus.resolve(statusCode.value());
    var title = status == null ? "Error" : status.getReasonPhrase();
    var problem = ProblemDetail.forStatusAndDetail(statusCode, title);
    return ResponseEntity.status(statusCode).headers(headers).body(problem);
  }

  private static ResponseEntity<Object> problem(
      HttpStatus status, String detail, Map<String, String> errors) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    if (!errors.isEmpty()) {
      problem.setProperty("errors", new LinkedHashMap<>(errors));
    }
    return ResponseEntity.status(status).body(problem);
  }
}
