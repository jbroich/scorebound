package de.scorebound.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(ApiException.class)
	ResponseEntity<SessionController.ApiProblem> handleApiException(ApiException exception) {
		return ResponseEntity.status(exception.getStatus())
				.contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.body(new SessionController.ApiProblem(exception.getStatus().value(), exception.getCode()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<SessionController.ApiProblem> handleIllegalArgument(IllegalArgumentException exception) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.body(new SessionController.ApiProblem(HttpStatus.BAD_REQUEST.value(), "validation_failed"));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<SessionController.ApiProblem> handleValidation(MethodArgumentNotValidException exception) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.body(new SessionController.ApiProblem(HttpStatus.BAD_REQUEST.value(), "validation_failed"));
	}

	@ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
	ResponseEntity<SessionController.ApiProblem> handleConcurrentChange(RuntimeException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.body(new SessionController.ApiProblem(HttpStatus.CONFLICT.value(), "concurrent_change"));
	}
}
