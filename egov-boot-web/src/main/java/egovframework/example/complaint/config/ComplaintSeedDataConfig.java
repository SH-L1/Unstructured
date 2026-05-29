package egovframework.example.complaint.config;

import egovframework.example.complaint.api.dto.ComplaintResponse;
import egovframework.example.complaint.api.dto.CreateComplaintRequest;
import egovframework.example.complaint.domain.Department;
import egovframework.example.complaint.domain.DocumentType;
import egovframework.example.complaint.domain.KnowledgeDocument;
import egovframework.example.complaint.repository.ComplaintRepository;
import egovframework.example.complaint.repository.DepartmentRepository;
import egovframework.example.complaint.repository.KnowledgeDocumentRepository;
import egovframework.example.complaint.service.ComplaintService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

		SeedDataService(DepartmentRepository departmentRepository, KnowledgeDocumentRepository knowledgeDocumentRepository) {
			this.departmentRepository = departmentRepository;
			this.knowledgeDocumentRepository = knowledgeDocumentRepository;
		}

		@Transactional
		void seed() {
			saveDepartment("RESOURCE_RECYCLING", "Resource Recycling Division", "Waste dumping, recycling and household waste complaints");
			saveDepartment("ROAD", "Road Management Division", "Road damage, potholes and sidewalk facility complaints");
			saveDepartment("TRAFFIC", "Traffic Administration Division", "Traffic facilities and illegal parking complaints");
			saveDepartment("CIVIL_AFFAIRS", "Civil Affairs Division", "General civil complaint routing");

			saveKnowledgeDocument(new KnowledgeDocument(
					DocumentType.LAW,
					"Waste Management Act handling basis",
					"National Law Information Center",
					null,
					"Household waste and illegal dumping complaints require site confirmation, removal review and enforcement review according to waste handling standards.",
					"waste,dumping,illegal dumping,garbage,household waste",
					"Waste Management Act"
			));
			saveKnowledgeDocument(new KnowledgeDocument(
					DocumentType.ORDINANCE,
					"Local waste handling ordinance sample",
					"Local Ordinance Information System",
					null,
					"Local governments may inspect reported dumping sites, remove household waste and guide residents according to local waste ordinances.",
					"waste,dumping,old furniture,recycling,local ordinance",
					"Local waste management ordinance"
			));
			saveKnowledgeDocument(new KnowledgeDocument(
					DocumentType.MANUAL,
					"Illegal dumping civil complaint response manual",
					"Civil complaint manual",
					null,
					"Responses should mention receipt of the complaint, responsible department, site inspection plan and additional confirmation if required.",
					"civil complaint,response manual,site inspection,waste removal",
					"Civil complaint response manual"
			));
		}

		private void saveDepartment(String code, String name, String description) {
			if (!departmentRepository.existsByCode(code)) {
				departmentRepository.save(new Department(code, name, description));
			}
		}

		private void saveKnowledgeDocument(KnowledgeDocument document) {
			if (!knowledgeDocumentRepository.existsByTitle(document.getTitle())) {
				knowledgeDocumentRepository.save(document);
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
			seedComplaint(
					"WEB",
					"Illegal dumping with waste bags and old furniture. Bad smell and insects. Please inspect and remove it.",
					"Seoul Jung-gu alley"
			);
			seedComplaint(
					"MOBILE",
					"Road surface is broken near the school entrance. There is a pothole and pedestrians may trip.",
					"Seoul Mapo-gu school entrance"
			);
			seedComplaint(
					"CALL_CENTER",
					"Traffic sign is hidden by a tree branch and drivers cannot see the no parking sign clearly.",
					"Seoul Jongno-gu main road"
			);
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
