package com.school.complaint.config;

import com.school.complaint.domain.Department;
import com.school.complaint.domain.DocumentType;
import com.school.complaint.domain.KnowledgeDocument;
import com.school.complaint.repository.DepartmentRepository;
import com.school.complaint.repository.KnowledgeDocumentRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@Profile("local")
public class LocalSeedDataConfig {

	@Bean
	CommandLineRunner seedLocalData(SeedDataService seedDataService) {
		return args -> seedDataService.seed();
	}

	@Configuration
	static class SeedDataService {

		private final DepartmentRepository departmentRepository;
		private final KnowledgeDocumentRepository knowledgeDocumentRepository;

		SeedDataService(DepartmentRepository departmentRepository, KnowledgeDocumentRepository knowledgeDocumentRepository) {
			this.departmentRepository = departmentRepository;
			this.knowledgeDocumentRepository = knowledgeDocumentRepository;
		}

		@Transactional
		void seed() {
			seedDepartments();
			seedKnowledgeDocuments();
		}

		private void seedDepartments() {
			saveDepartment("RESOURCE_RECYCLING", "자원순환과", "생활폐기물, 불법 투기, 재활용 관련 민원 담당");
			saveDepartment("ROAD", "도로과", "도로 파손, 포트홀, 보도블록 관련 민원 담당");
			saveDepartment("TRAFFIC", "교통행정과", "불법 주정차, 교통시설 관련 민원 담당");
			saveDepartment("CIVIL_AFFAIRS", "민원처리과", "일반 생활 민원 접수 및 부서 배정");
		}

		private void saveDepartment(String code, String name, String description) {
			if (!departmentRepository.existsByCode(code)) {
				departmentRepository.save(new Department(code, name, description));
			}
		}

		private void seedKnowledgeDocuments() {
			saveKnowledgeDocument(new KnowledgeDocument(
					DocumentType.LAW,
					"폐기물관리법 생활폐기물 처리 기준",
					"국가법령정보센터",
					null,
					"생활폐기물은 지방자치단체의 처리 기준에 따라 배출 및 수거되어야 하며, 무단 투기 행위는 현장 확인과 행정 절차의 대상이 될 수 있다.",
					"불법 투기,폐기물,생활폐기물,무단 투기",
					"폐기물관리법"
			));
			saveKnowledgeDocument(new KnowledgeDocument(
					DocumentType.ORDINANCE,
					"지자체 폐기물 관리 조례 예시",
					"자치법규정보시스템",
					null,
					"생활폐기물 배출 장소와 방법을 위반하거나 무단으로 폐기물을 투기한 경우 담당 부서가 현장 확인 후 수거 및 계도 절차를 진행한다.",
					"불법 투기,폐가구,쓰레기,자원순환과",
					"지자체 폐기물 관리 조례"
			));
			saveKnowledgeDocument(new KnowledgeDocument(
					DocumentType.MANUAL,
					"불법 투기 민원 처리 매뉴얼",
					"민원 처리 매뉴얼",
					null,
					"불법 투기 신고 민원은 접수 내용, 위치, 사진 자료를 확인한 뒤 자원순환 담당 부서에 배정하고 현장 확인 및 수거 가능 여부를 안내한다.",
					"불법 투기,민원 처리,현장 확인,수거 조치",
					"민원 처리 매뉴얼"
			));
			saveKnowledgeDocument(new KnowledgeDocument(
					DocumentType.CASE,
					"불법 투기 신고 답변 사례",
					"과거 답변 사례",
					null,
					"귀하께서 신고하신 폐기물 방치 사항은 담당 부서에서 현장 확인 후 관련 기준에 따라 처리할 예정입니다. 추가 확인이 필요한 경우 별도 연락드리겠습니다.",
					"불법 투기,답변 사례,공문 초안,폐기물",
					"유사 민원 답변 사례"
			));
		}

		private void saveKnowledgeDocument(KnowledgeDocument document) {
			if (!knowledgeDocumentRepository.existsByTitle(document.getTitle())) {
				knowledgeDocumentRepository.save(document);
			}
		}
	}
}
