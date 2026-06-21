package io.github.futomaru.todoapp.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/// アプリ全体の例外を ProblemDetail (RFC 7807) に変換する。
@RestControllerAdvice
public class GlobalExceptionHandler {

  /// `TodoNotFoundException` を 404 ProblemDetail に変換する。
  @ExceptionHandler(TodoNotFoundException.class)
  public ProblemDetail handleTodoNotFoundException(
      TodoNotFoundException ex, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    problem.setInstance(URI.create(request.getRequestURI()));
    return problem;
  }

  /// `@RequestBody @Valid` のバリデーション失敗を 400 ProblemDetail に変換する。
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
    problem.setInstance(URI.create(request.getRequestURI()));

    List<Map<String, String>> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                fe ->
                    Map.of(
                        "field",
                        fe.getField(),
                        "message",
                        fe.getDefaultMessage() != null ? fe.getDefaultMessage() : ""))
            .toList();
    problem.setProperty("errors", errors);
    return problem;
  }

  /// `@PathVariable @Min` などメソッド引数バリデーション失敗を 400 ProblemDetail に変換する。
  @ExceptionHandler(HandlerMethodValidationException.class)
  public ProblemDetail handleHandlerMethodValidation(
      HandlerMethodValidationException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
    problem.setInstance(URI.create(request.getRequestURI()));

    List<Map<String, String>> errors =
        ex.getParameterValidationResults().stream()
            .flatMap(
                vr ->
                    vr.getResolvableErrors().stream()
                        .map(
                            err -> {
                              String field;
                              if (err instanceof FieldError fieldError) {
                                field = fieldError.getField();
                              } else {
                                String paramName = vr.getMethodParameter().getParameterName();
                                field = paramName != null ? paramName : "";
                              }
                              return Map.of(
                                  "field",
                                  field,
                                  "message",
                                  err.getDefaultMessage() != null ? err.getDefaultMessage() : "");
                            }))
            .toList();
    problem.setProperty("errors", errors);
    return problem;
  }

  /// 必須クエリパラメータ欠落を 400 ProblemDetail に変換する。
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ProblemDetail handleMissingServletRequestParameter(
      MissingServletRequestParameterException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Required parameter '" + ex.getParameterName() + "' is not present");
    problem.setInstance(URI.create(request.getRequestURI()));
    return problem;
  }
}
