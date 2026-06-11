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
			saveDepartment("ENVIRONMENT", "Asan Environment", "Asan routing candidate: environment, noise, odor");
			saveDepartment("BUILDING_HOUSING", "Asan Building And Housing", "Asan routing candidate: building and housing");
			saveDepartment("PARK_GREEN", "Asan Park And Green Space", "Asan routing candidate: parks and green space");
			saveDepartment("WATER_SEWER", "Asan Water And Sewer", "Asan routing candidate: water and sewer");
			saveDepartment("HEALTH_SANITATION", "Asan Health And Sanitation", "Asan routing candidate: health and sanitation");
			saveDepartment("ANIMAL_LIVESTOCK", "Asan Animal And Livestock", "Asan routing candidate: animals and livestock");
			saveDepartment("URBAN_MANAGEMENT", "Asan Urban Management", "Asan routing candidate: advertising and street management");
			saveDepartment("WELFARE", "Asan Welfare", "Asan routing candidate: welfare access");

			seedLegacyDocument(
					DocumentType.LAW,
					"Legacy waste handling summary",
					"Legacy seed reference",
					"C:\\Users\\user\\Downloads\\Unstructured\\ai-rag-engine\\data\\minwon_manuals\\legacy_waste.txt",
					"폐기물관리법 제8조(폐기물의 투기 금지 등) 및 관할 지자체 조례에 의거하여, 누구든지 지정된 구역 외의 장소에 생활폐기물을 무단 투기해서는 아니 됩니다. 이를 위반할 시 100만 원 이하의 과태료가 부과되며 행정처분 절차가 진행됩니다.",
					"waste,dumping,garbage,illegal dumping",
					"Waste handling reference",
					egovframework.example.complaint.domain.KnowledgePurpose.OFFICIAL_LAW,
					egovframework.example.complaint.domain.KnowledgeVerificationStatus.VERIFIED_OFFICIAL,
					"NATIONAL"
			);
			seedLegacyDocument(
					DocumentType.MANUAL,
					"Legacy road damage response summary",
					"Legacy seed reference",
					"C:\\Users\\user\\Downloads\\Unstructured\\ai-rag-engine\\data\\minwon_manuals\\legacy_road.txt",
					"도로법 제63조 및 도로 유지관리 매뉴얼에 따라, 도로의 포트홀 등 차량 통행 및 보행자 안전을 저해하는 노면 파손이 발견될 시 즉시 현장 위치를 특정하고 위험도 평가를 거친 후 긴급 보수 조치 및 재발 방지 대책을 지체 없이 시행하여야 합니다.",
					"road,pothole,sidewalk,damage",
					"Road facility handling reference",
					egovframework.example.complaint.domain.KnowledgePurpose.PROCEDURE,
					egovframework.example.complaint.domain.KnowledgeVerificationStatus.VERIFIED_INTERNAL,
					"ASAN"
			);
			seedLegacyDocument(
					DocumentType.LAW,
					"Legacy illegal parking response summary",
					"Legacy seed reference",
					"C:\\Users\\user\\Downloads\\Unstructured\\ai-rag-engine\\data\\minwon_manuals\\legacy_parking.txt",
					"도로교통법 제32조(정차 및 주차의 금지)에 따라 버스정류장 및 보행자 통행 안전을 저해하는 구역에 정차하거나 주차해서는 아니 됩니다. 위반 차량 발견 시 관련 규정에 의거하여 집중 단속하고 과태료 부과 및 즉각적인 견인 처리를 실시할 수 있습니다.",
					"traffic,parking,illegal parking",
					"Traffic handling reference",
					egovframework.example.complaint.domain.KnowledgePurpose.OFFICIAL_LAW,
					egovframework.example.complaint.domain.KnowledgeVerificationStatus.VERIFIED_OFFICIAL,
					"NATIONAL"
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
				String sourceUrl,
				String content,
				String keywords,
				String legalBasis,
				egovframework.example.complaint.domain.KnowledgePurpose purpose,
				egovframework.example.complaint.domain.KnowledgeVerificationStatus verificationStatus,
				String jurisdictionCode
		) {
			KnowledgeDocument document = knowledgeDocumentRepository.findByTitle(title)
					.orElseGet(() -> {
						KnowledgeDocument doc = new KnowledgeDocument(
								type,
								title,
								sourceName,
								sourceUrl,
								content,
								keywords,
								legalBasis
						);
						doc.verifyForTest(purpose, verificationStatus, jurisdictionCode, java.time.LocalDate.now().minusYears(1), java.time.LocalDate.now().plusYears(10));
						return knowledgeDocumentRepository.save(doc);
					});
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
