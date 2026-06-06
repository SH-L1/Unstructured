package egovframework.example.complaint.api;

import egovframework.example.complaint.api.dto.ApiError;
import egovframework.example.complaint.api.dto.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(EntityNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotFound(EntityNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.error("NOT_FOUND", exception.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
		List<String> details = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + " " + error.getDefaultMessage())
				.toList();
		ApiError error = new ApiError("VALIDATION_FAILED", "Request validation failed", details);
		return ResponseEntity.badRequest()
				.body(new ApiResponse<>(false, null, error.message(), error));
	}

	@ExceptionHandler({
			MethodArgumentTypeMismatchException.class,
			MissingServletRequestParameterException.class,
			IllegalArgumentException.class
	})
	public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception exception) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.error("BAD_REQUEST", exception.getMessage()));
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ApiResponse<Void>> handleConflict(IllegalStateException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ApiResponse.error("WORKFLOW_CONFLICT", exception.getMessage()));
	}

	@ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
	public ResponseEntity<ApiResponse<Void>> handleConcurrentConflict(RuntimeException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ApiResponse.error("CONCURRENT_CONFLICT", "A concurrent request changed or reserved this resource"));
	}
}
