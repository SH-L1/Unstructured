package egovframework.example.complaint.service;

import java.time.Instant;

public record StoredObjectReference(String storageKey, Instant lastModifiedAt) {
}
