package egovframework.example.complaint.api;

import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(EntityNotFoundException.class)
	public ProblemDetail handleNotFound(EntityNotFoundException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
		problem.setTitle("Resource not found");
		problem.setProperty("timestamp", LocalDateTime.now());
		return problem;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		problem.setTitle("Validation failed");
		problem.setDetail(exception.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(error -> error.getField() + " " + error.getDefaultMessage())
				.orElse("Invalid request"));
		problem.setProperty("timestamp", LocalDateTime.now());
		return problem;
	}
}
