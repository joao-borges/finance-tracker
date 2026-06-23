package ca.joaoborges.finance.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates persistence-layer constraint violations (e.g. a duplicate
 * category-group name) into a clean 409 instead of a 500. Controllers still use
 * {@code ResponseStatusException} for their own 400/404s, which Spring maps
 * directly.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail onDataIntegrityViolation(final DataIntegrityViolationException exception) {
        final ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Constraint violation");
        problem.setDetail("That value conflicts with an existing record (it may already exist).");
        return problem;
    }

}
