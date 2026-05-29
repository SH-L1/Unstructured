package egovframework.example.complaint.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ApiError(
		String code,
		String message,
		List<String> details,
		LocalDateTime timestamp
) {
	public ApiError(String code, String message) {
		this(code, message, List.of(), LocalDateTime.now());
	}

	public ApiError(String code, String message, List<String> details) {
		this(code, message, details, LocalDateTime.now());
	}
}
