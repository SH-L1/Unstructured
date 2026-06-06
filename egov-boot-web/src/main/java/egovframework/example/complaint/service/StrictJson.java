package egovframework.example.complaint.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

final class StrictJson {

	private StrictJson() {
	}

	static JsonNode readSingleDocument(ObjectMapper objectMapper, String value, String label) throws IOException {
		try (JsonParser parser = objectMapper.createParser(value)) {
			JsonNode root = objectMapper.readTree(parser);
			if (root == null) {
				throw new IllegalStateException(label + " must contain exactly one JSON document");
			}
			try {
				if (parser.nextToken() != null) {
					throw new IllegalStateException(label + " must contain exactly one JSON document");
				}
			}
			catch (IOException exception) {
				throw new IllegalStateException(label + " must contain exactly one JSON document", exception);
			}
			return root;
		}
	}
}
