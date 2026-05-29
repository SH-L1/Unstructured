package egovframework.example.complaint.api.dto;

public record ApiResponse<T>(
		boolean success,
		T data,
		String message,
		ApiError error
) {
	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(true, data, null, null);
	}

	public static <T> ApiResponse<T> created(T data) {
		return new ApiResponse<>(true, data, "created", null);
	}

	public static <T> ApiResponse<T> error(String code, String message) {
		return new ApiResponse<>(false, null, message, new ApiError(code, message));
	}
}
