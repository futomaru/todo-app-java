package io.github.futomaru.todoapp.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/// アプリ固有の例外を ProblemDetail (RFC 7807) に変換する。
///
/// Spring 標準例外 (`@Valid` のバリデーション失敗、必須クエリパラメータ欠落など) は
/// 親クラス `ResponseEntityExceptionHandler` が提供するハンドラがそのまま処理し、
/// Spring 6 のデフォルトとして ProblemDetail 形式で応答する。
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  /// `TodoNotFoundException` を 404 ProblemDetail に変換する。
  @ExceptionHandler(TodoNotFoundException.class)
  public ProblemDetail handleTodoNotFoundException(
      TodoNotFoundException ex, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    problem.setInstance(URI.create(request.getRequestURI()));
    return problem;
  }
}
