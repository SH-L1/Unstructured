package egovframework.example.complaint.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import egovframework.example.complaint.domain.Complaint;
import egovframework.example.complaint.domain.ComplaintAnalysis;
import egovframework.example.complaint.domain.Department;
import egovframework.example.complaint.domain.DocumentType;
import egovframework.example.complaint.domain.KnowledgeDocument;
import egovframework.example.complaint.domain.KnowledgeDocumentChunk;
import egovframework.example.complaint.domain.KnowledgePurpose;
import egovframework.example.complaint.domain.KnowledgeVerificationStatus;
import egovframework.example.complaint.repository.DepartmentRepository;
import egovframework.example.complaint.repository.KnowledgeDocumentChunkRepository;
import egovframework.example.complaint.repository.KnowledgeDocumentRepository;
import egovframework.example.complaint.service.DraftGenerationClient;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
class ComplaintTestSupportConfig {

	@Bean
	CommandLineRunner complaintTestFixtures(
			DepartmentRepository departmentRepository,
			KnowledgeDocumentRepository knowledgeDocumentRepository,
			KnowledgeDocumentChunkRepository knowledgeDocumentChunkRepository
	) {
		return args -> {
			saveDepartment(departmentRepository, "RESOURCE_RECYCLING", "Resource Recycling", "Waste and recycling complaints");
			saveDepartment(departmentRepository, "ROAD", "Road Management", "Road, sidewalk, and pothole complaints");
			saveDepartment(departmentRepository, "TRAFFIC", "Traffic Administration", "Parking and traffic complaints");
			saveDepartment(departmentRepository, "CIVIL_AFFAIRS", "Civil Affairs", "General intake and routing");
			saveDepartment(departmentRepository, "SAFETY_CONTROL", "Safety Control", "Safety escalation review");

			saveKnowledge(
					knowledgeDocumentRepository,
					knowledgeDocumentChunkRepository,
					"Legacy waste handling summary",
					"Official waste handling test fixture",
					"Illegal dumping requires staff review and may require official waste law evidence before legal claims.",
					"waste,dumping,garbage,illegal dumping",
					"Waste Management Act"
			);
			saveKnowledge(
					knowledgeDocumentRepository,
					knowledgeDocumentChunkRepository,
					"Legacy road damage response summary",
					"Road damage procedure test fixture",
					"Road damage complaints require location confirmation, department review, and field inspection.",
					"road,pothole,sidewalk,damage",
					"Road facility handling procedure"
			);
			saveKnowledge(
					knowledgeDocumentRepository,
					knowledgeDocumentChunkRepository,
					"Legacy illegal parking response summary",
					"Traffic handling test fixture",
					"Illegal parking complaints require traffic department review and evidence confirmation.",
					"traffic,parking,illegal parking",
					"Road Traffic Act"
			);
		};
	}

	@Bean
	DraftGenerationClient testDraftGenerationClient(ObjectMapper objectMapper) {
		return new TestDraftGenerationClient(objectMapper);
	}

	private void saveDepartment(DepartmentRepository repository, String code, String name, String description) {
		if (!repository.existsByCode(code)) {
			repository.save(new Department(code, name, description));
		}
	}

	private void saveKnowledge(
			KnowledgeDocumentRepository documentRepository,
			KnowledgeDocumentChunkRepository chunkRepository,
			String title,
			String sourceName,
			String content,
			String keywords,
			String legalBasis
	) {
		KnowledgeDocument document = documentRepository.findByTitle(title)
				.orElseGet(() -> {
					KnowledgeDocument created = new KnowledgeDocument(
							DocumentType.LAW,
							title,
							sourceName,
							"test-fixture://" + title.replace(' ', '-').toLowerCase(),
							content,
							keywords,
							legalBasis
					);
					created.verifyForTest(
							KnowledgePurpose.OFFICIAL_LAW,
							KnowledgeVerificationStatus.VERIFIED_OFFICIAL,
							"NATIONAL",
							LocalDate.now().minusYears(1),
							null
					);
					return documentRepository.save(created);
				});
		if (!chunkRepository.existsByKnowledgeDocumentIdAndChunkIndex(document.getId(), 0)) {
			chunkRepository.save(new KnowledgeDocumentChunk(document, 0, content, keywords, legalBasis));
		}
	}

	private static final class TestDraftGenerationClient implements DraftGenerationClient {

		private final ObjectMapper objectMapper;

		private TestDraftGenerationClient(ObjectMapper objectMapper) {
			this.objectMapper = objectMapper;
		}

		@Override
		public String generateDraft(
				Complaint complaint,
				ComplaintAnalysis analysis,
				List<KnowledgeDocument> documents,
				List<String> approvedAttachmentTexts
		) {
			List<String> evidenceIds = documents.stream()
					.map(document -> String.valueOf(document.getId()))
					.toList();
			try {
				return objectMapper.writeValueAsString(Map.of(
						"schemaVersion", "draft-claims-v1",
						"claims", List.of(
								claim("Staff review required.", "REVIEW_NOTICE", evidenceIds),
								claim("Receipt summary: " + analysis.getIntent(), "ACKNOWLEDGEMENT", evidenceIds),
								claim("Recommended department: " + analysis.getDepartment().getName(),
										"PROPOSED_NEXT_STEP", evidenceIds),
								claim("Verified evidence reviewed for this draft.",
										"EVIDENCE_BASED_FINDING", evidenceIds)
						)
				));
			}
			catch (JsonProcessingException exception) {
				throw new IllegalStateException("Failed to create test draft", exception);
			}
		}

		private Map<String, Object> claim(String text, String type, List<String> evidenceIds) {
			return Map.of("text", text, "claimType", type, "evidenceIds", evidenceIds);
		}
	}
}
