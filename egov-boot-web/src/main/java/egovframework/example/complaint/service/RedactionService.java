package egovframework.example.complaint.service;

import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class RedactionService {

	private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
	private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:01[016789]|02|0[3-6][1-5])-?\\d{3,4}-?\\d{4}(?!\\d)");
	private static final Pattern RESIDENT_NUMBER = Pattern.compile("(?<!\\d)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])-?[1-4]\\d{6}(?!\\d)");

	public String redact(String value) {
		return inspect(value).redactedText();
	}

	public RedactionResult inspect(String value) {
		if (value == null || value.isBlank()) {
			return new RedactionResult(value, "[]");
		}
		boolean emailFound = EMAIL.matcher(value).find();
		boolean phoneFound = PHONE.matcher(value).find();
		boolean residentNumberFound = RESIDENT_NUMBER.matcher(value).find();
		String redacted = EMAIL.matcher(value).replaceAll("[REDACTED_EMAIL]");
		redacted = PHONE.matcher(redacted).replaceAll("[REDACTED_PHONE]");
		redacted = RESIDENT_NUMBER.matcher(redacted).replaceAll("[REDACTED_ID]");
		String findings = "["
				+ (emailFound ? "\"EMAIL\"," : "")
				+ (phoneFound ? "\"PHONE\"," : "")
				+ (residentNumberFound ? "\"RESIDENT_NUMBER\"," : "")
				+ "]";
		findings = findings.replace(",]", "]");
		return new RedactionResult(redacted, findings);
	}

	public boolean containsSensitivePattern(String value) {
		return value != null && !value.equals(redact(value));
	}

	public record RedactionResult(String redactedText, String findingsJson) {
	}
}
