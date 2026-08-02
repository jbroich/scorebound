package de.scorebound.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

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
}
