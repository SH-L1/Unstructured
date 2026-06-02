package egovframework.example.complaint.config;

import egovframework.example.complaint.api.dto.ComplaintResponse;
import egovframework.example.complaint.api.dto.CreateComplaintRequest;
import egovframework.example.complaint.domain.Department;
import egovframework.example.complaint.domain.DocumentType;
import egovframework.example.complaint.domain.KnowledgeDocument;
import egovframework.example.complaint.domain.KnowledgeDocumentChunk;
import egovframework.example.complaint.repository.ComplaintRepository;
import egovframework.example.complaint.repository.DepartmentRepository;
import egovframework.example.complaint.repository.KnowledgeDocumentChunkRepository;
import egovframework.example.complaint.repository.KnowledgeDocumentRepository;
import egovframework.example.complaint.service.ComplaintService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class ComplaintSeedDataConfig {

	@Bean
	@Order(1)
	CommandLineRunner seedComplaintData(SeedDataService seedDataService) {
		return args -> seedDataService.seed();
	}

	@Bean
	@Order(2)
	CommandLineRunner seedDemoComplaints(DemoComplaintSeedService demoComplaintSeedService) {
		return args -> demoComplaintSeedService.seed();
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
			saveDepartment("RESOURCE_RECYCLING", "자원순환과", "생활폐기물, 무단투기, 재활용, 악취 관련 민원 처리");
			saveDepartment("ROAD", "도로관리과", "도로 파손, 포트홀, 보도블록, 도로 시설물 관련 민원 처리");
			saveDepartment("TRAFFIC", "교통행정과", "불법주정차, 교통표지, 신호, 차량 통행 관련 민원 처리");
			saveDepartment("CIVIL_AFFAIRS", "민원행정과", "일반 민원 접수, 부서 라우팅, 민원 처리 지원");
			saveDepartment("SAFETY_CONTROL", "재난안전과", "생화학 위험물, 폭발물 의심, 유해화학물질, 공공안전 위협 민원 긴급 대응 및 관계기관 연계");

			KnowledgeDocument wasteLaw = saveKnowledgeDocument(new KnowledgeDocument(
					DocumentType.LAW,
					"Waste Management Act handling basis",
					"National law reference",
					null,
					"When illegal dumping or household waste complaints are received, the responsible department checks the site, arranges removal, and reviews whether enforcement action is needed.",
					"waste,dumping,garbage,trash,illegal dumping,쓰레기,폐기물,무단투기,생활폐기물,악취",
					"Waste Management Act and local waste handling ordinances"
			));
			KnowledgeDocument wasteManual = saveKnowledgeDocument(new KnowledgeDocument(
					DocumentType.MANUAL,
					"Illegal dumping civil complaint response manual",
					"Civil complaint manual",
					null,
					"A waste dumping reply should include receipt confirmation, expected site inspection, responsible department, cleanup review, and follow-up notice.",
					"waste,dumping,site inspection,cleanup,쓰레기,폐기물,무단투기,현장확인,수거",
					"Civil complaint response manual"
			));
			KnowledgeDocument roadManual = saveKnowledgeDocument(new KnowledgeDocument(
					DocumentType.MANUAL,
					"Road damage complaint response manual",
					"Civil complaint manual",
					null,
					"Road damage complaints require location confirmation, risk assessment, field inspection, emergency repair review, and schedule guidance.",
					"road,pothole,sidewalk,broken,도로,포트홀,파손,보도,보수,현장점검",
					"Road facility complaint handling standard"
			));
			KnowledgeDocument trafficManual = saveKnowledgeDocument(new KnowledgeDocument(
					DocumentType.MANUAL,
					"Traffic facility and illegal parking complaint manual",
					"Civil complaint manual",
					null,
					"Traffic sign, signal, vehicle passage, and illegal parking complaints are reviewed by the traffic department after site confirmation.",
					"traffic,parking,sign,signal,교통,불법주정차,교통표지,신호,차량",
					"Traffic civil complaint handling standard"
			));
			KnowledgeDocument hazardousManual = saveKnowledgeDocument(new KnowledgeDocument(
					DocumentType.MANUAL,
					"생화학 위험물 및 폭발물 의심 신고 긴급 대응 매뉴얼",
					"재난안전 민원 대응 기준",
					null,
					"도로, 공공장소, 생활권에서 생화학 물질, 유해화학물질, 폭발물 또는 폭탄으로 의심되는 물체가 신고되면 담당자는 민원인에게 접근 금지를 안내하고 현장 통제, 경찰·소방·재난안전 부서 전파, 관계기관 확인을 우선 검토한다. 폐기물 또는 일반 도로 민원으로 단정하지 않는다.",
					"생화학,위험물,폭탄,폭발물,화학물질,유해물질,재난,경찰,소방,biohazard,biochemical,hazardous,chemical,bomb,explosive,emergency",
					"재난 및 안전관리 기본법, 유해화학물질 관리 관련 기준, 경찰·소방 관계기관 긴급 대응 절차"
			));
			KnowledgeDocument hazardousLaw = saveKnowledgeDocument(new KnowledgeDocument(
					DocumentType.LAW,
					"공공안전 위해물질 신고 처리 근거",
					"재난안전 관계 법령 및 대응 절차",
					null,
					"폭발물, 위험물, 유해화학물질 등 공공안전을 위협할 수 있는 사안은 일반 민원 처리보다 인명 안전과 현장 통제를 우선한다. 지자체 담당 부서는 경찰, 소방, 재난안전 관련 기관과 협조하여 현장 확인 및 통제 필요성을 검토한다.",
					"위험물,폭발물,폭탄,유해화학물질,생화학,공공안전,현장통제,관계기관,경찰,소방",
					"재난 및 안전관리 기본법, 위험물 안전관리 및 관계기관 공동대응 기준"
			));

			saveKnowledgeChunk(wasteLaw, 0, wasteLaw.getContent(), wasteLaw.getKeywords(), wasteLaw.getLegalBasis());
			saveKnowledgeChunk(wasteManual, 0, wasteManual.getContent(), wasteManual.getKeywords(), wasteManual.getLegalBasis());
			saveKnowledgeChunk(roadManual, 0, roadManual.getContent(), roadManual.getKeywords(), roadManual.getLegalBasis());
			saveKnowledgeChunk(trafficManual, 0, trafficManual.getContent(), trafficManual.getKeywords(), trafficManual.getLegalBasis());
			saveKnowledgeChunk(hazardousManual, 0, hazardousManual.getContent(), hazardousManual.getKeywords(), hazardousManual.getLegalBasis());
			saveKnowledgeChunk(hazardousLaw, 0, hazardousLaw.getContent(), hazardousLaw.getKeywords(), hazardousLaw.getLegalBasis());
		}

		private void saveDepartment(String code, String name, String description) {
			if (!departmentRepository.existsByCode(code)) {
				departmentRepository.save(new Department(code, name, description));
			}
		}

		private KnowledgeDocument saveKnowledgeDocument(KnowledgeDocument document) {
			return knowledgeDocumentRepository.findByTitle(document.getTitle())
					.orElseGet(() -> knowledgeDocumentRepository.save(document));
		}

		private void saveKnowledgeChunk(KnowledgeDocument document, int chunkIndex, String content, String keywords, String legalBasis) {
			if (!knowledgeDocumentChunkRepository.existsByKnowledgeDocumentIdAndChunkIndex(document.getId(), chunkIndex)) {
				knowledgeDocumentChunkRepository.save(new KnowledgeDocumentChunk(document, chunkIndex, content, keywords, legalBasis));
			}
		}
	}

	@Configuration
	static class DemoComplaintSeedService {

		private final ComplaintRepository complaintRepository;
		private final ComplaintService complaintService;
		private final boolean demoEnabled;

		DemoComplaintSeedService(
				ComplaintRepository complaintRepository,
				ComplaintService complaintService,
				@Value("${app.seed.demo-enabled:true}") boolean demoEnabled
		) {
			this.complaintRepository = complaintRepository;
			this.complaintService = complaintService;
			this.demoEnabled = demoEnabled;
		}

		@Transactional
		void seed() {
			if (!demoEnabled) {
				return;
			}
			seedComplaint("WEB", "아파트 공터에 쓰레기가 무단투기되어 악취가 심합니다. 현장 확인 후 수거 조치를 요청합니다.", "서울 중구 아파트 공터");
			seedComplaint("MOBILE", "학교 앞 도로에 포트홀이 생겨 학생들이 등하교할 때 위험합니다. 현장 확인과 보수를 요청합니다.", "서울 마포구 학교 앞");
			seedComplaint("CALL_CENTER", "도로변 교통표지판이 파손되어 운전자가 잘 보지 못합니다. 정비가 필요합니다.", "서울 종로구 도로변");
		}

		private void seedComplaint(String sourceChannel, String rawText, String locationText) {
			if (complaintRepository.existsByRawText(rawText)) {
				return;
			}
			ComplaintResponse response = complaintService.create(new CreateComplaintRequest(sourceChannel, rawText, locationText));
			complaintService.analyze(response.id());
			complaintService.generateDraft(response.id());
		}
	}
}
