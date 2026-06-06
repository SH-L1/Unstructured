package egovframework.example.complaint.config;

import egovframework.example.complaint.domain.Department;
import egovframework.example.complaint.domain.DocumentType;
import egovframework.example.complaint.domain.KnowledgeDocument;
import egovframework.example.complaint.domain.KnowledgeDocumentChunk;
import egovframework.example.complaint.repository.DepartmentRepository;
import egovframework.example.complaint.repository.KnowledgeDocumentChunkRepository;
import egovframework.example.complaint.repository.KnowledgeDocumentRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class ComplaintSeedDataConfig {

	@Bean
	CommandLineRunner seedReferenceData(SeedDataService seedDataService) {
		return args -> seedDataService.seed();
	}

	@Configuration
	static class SeedDataService {

		private final DepartmentRepository departmentRepository;
		private final KnowledgeDocumentRepository knowledgeDocumentRepository;
		private final KnowledgeDocumentChunkRepository knowledgeDocumentChunkRepository;

		SeedDataService(
				DepartmentRepository departmentRepository,
				KnowledgeDocumentRepository knowledgeDocumentRepository,
				KnowledgeDocumentChunkRepository knowledgeDocumentChunkRepository
		) {
			this.departmentRepository = departmentRepository;
			this.knowledgeDocumentRepository = knowledgeDocumentRepository;
			this.knowledgeDocumentChunkRepository = knowledgeDocumentChunkRepository;
		}

		@Transactional
		void seed() {
			saveDepartment("RESOURCE_RECYCLING", "Demo Resource Recycling", "SYNTHETIC_DEMO: waste complaint candidate");
			saveDepartment("ROAD", "Demo Road Management", "SYNTHETIC_DEMO: road damage candidate");
			saveDepartment("TRAFFIC", "Demo Traffic Administration", "SYNTHETIC_DEMO: illegal parking candidate");
			saveDepartment("CIVIL_AFFAIRS", "Demo Civil Affairs", "SYNTHETIC_DEMO: intake and jurisdiction review");
			saveDepartment("SAFETY_CONTROL", "Demo Safety Control", "SYNTHETIC_DEMO: safety escalation candidate");

			seedLegacyDocument(
					DocumentType.LAW,
					"Legacy waste handling summary",
					"Legacy seed reference",
					"Illegal dumping reports require site and jurisdiction confirmation before action.",
					"waste,dumping,garbage,illegal dumping",
					"Waste handling reference"
			);
			seedLegacyDocument(
					DocumentType.MANUAL,
					"Legacy road damage response summary",
					"Legacy seed reference",
					"Road damage reports require exact location confirmation and a field risk assessment.",
					"road,pothole,sidewalk,damage",
					"Road facility handling reference"
			);
			seedLegacyDocument(
					DocumentType.MANUAL,
					"Legacy illegal parking response summary",
					"Legacy seed reference",
					"Illegal parking reports require location and jurisdiction confirmation.",
					"traffic,parking,illegal parking",
					"Traffic handling reference"
			);
		}

		private void saveDepartment(String code, String name, String description) {
			if (!departmentRepository.existsByCode(code)) {
				departmentRepository.save(new Department(code, name, description));
			}
		}

		private void seedLegacyDocument(
				DocumentType type,
				String title,
				String sourceName,
				String content,
				String keywords,
				String legalBasis
		) {
			KnowledgeDocument document = knowledgeDocumentRepository.findByTitle(title)
					.orElseGet(() -> knowledgeDocumentRepository.save(new KnowledgeDocument(
							type,
							title,
							sourceName,
							"https://example.invalid/unverified-legacy",
							content,
							keywords,
							legalBasis
					)));
			if (!knowledgeDocumentChunkRepository.existsByKnowledgeDocumentIdAndChunkIndex(document.getId(), 0)) {
				knowledgeDocumentChunkRepository.save(new KnowledgeDocumentChunk(
						document,
						0,
						content,
						keywords,
						legalBasis
				));
			}
		}
	}
}
