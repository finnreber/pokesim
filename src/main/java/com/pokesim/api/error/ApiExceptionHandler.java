package com.pokesim.api.error;

import com.pokesim.engine.IllegalActionException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * One place that turns exceptions into HTTP errors, all in the standard RFC 9457 "problem details" format
 * (application/problem+json). Highest precedence so these handlers win over Spring's defaults.
 *
 * Status codes: 400 = request is malformed/invalid shape, 401 = who are you?, 404 = not found (or not yours),
 * 409 = conflicts with existing data, 422 = well-formed but breaks the game's rules.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail badRequest(BadRequestException e) {
        return problem(HttpStatus.BAD_REQUEST, "Bad request", e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail conflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, "Conflict", e.getMessage());
    }

    /** Safety net for races: two requests passing an "exists?" check at once, then hitting a UNIQUE constraint. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integrity(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "Conflict", "The request conflicts with existing data");
    }

    /** Someone else changed the same row first (version check failed), e.g. two people joining one lobby. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail staleWrite(OptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "Conflict", "Someone else changed this at the same time; reload and try again");
    }

    /** The engine refused an action that is not legal right now (no PP left, fainted target, wrong phase...). */
    @ExceptionHandler(IllegalActionException.class)
    public ProblemDetail illegalAction(IllegalActionException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Illegal action", e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail unauthorized(UnauthorizedException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", e.getMessage());
    }

    @ExceptionHandler(InvalidTeamException.class)
    public ProblemDetail invalidTeam(InvalidTeamException e) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Illegal team", e.getMessage());
        problem.setProperty("errors", e.getErrors());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", "Request validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
