package egovframework.example.complaint.service;

import egovframework.example.complaint.api.dto.DepartmentResponse;
import egovframework.example.complaint.domain.Department;
import egovframework.example.complaint.repository.DepartmentRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DepartmentDirectoryService implements DepartmentRoutingService {

	private final DepartmentRepository departmentRepository;

	public DepartmentDirectoryService(DepartmentRepository departmentRepository) {
		this.departmentRepository = departmentRepository;
	}

	@Override
	public String route(String intent) {
		return departmentRepository.findByCode(routeCode(intent))
				.filter(Department::isActive)
				.or(() -> departmentRepository.findByCode("CIVIL_AFFAIRS").filter(Department::isActive))
				.map(Department::getName)
				.orElseThrow(() -> new IllegalStateException("No active department directory is loaded"));
	}

	@Override
	public List<DepartmentResponse> findDepartments() {
		return departmentRepository.findAll().stream()
				.filter(Department::isActive)
				.map(department -> new DepartmentResponse(
						department.getCode(),
						department.getName(),
						department.getDescription()
				))
				.toList();
	}

	private String routeCode(String intent) {
		if (intent == null || intent.isBlank()) {
			return "CIVIL_AFFAIRS";
		}
		String normalized = intent.toLowerCase();
		if (containsAny(normalized, "waste", "dumping", "illegal_dumping", "쓰레기", "폐기물", "무단투기", "재활용")) {
			return "RESOURCE_RECYCLING";
		}
		if (containsAny(normalized, "road", "road_damage", "pothole", "street", "sidewalk", "도로", "포트홀", "보도", "가로등")) {
			return "ROAD";
		}
		if (containsAny(normalized, "parking", "traffic", "illegal_parking", "주정차", "불법주차", "교통", "표지판", "신호")) {
			return "TRAFFIC";
		}
		if (containsAny(normalized, "water", "sewer", "drain", "상수도", "하수도", "배수", "누수", "맨홀")) {
			return "WATER_SEWER";
		}
		if (containsAny(normalized, "park", "green", "tree", "공원", "녹지", "가로수", "놀이터")) {
			return "PARK_GREEN";
		}
		if (containsAny(normalized, "building", "construction", "housing", "건축", "건물", "주택", "공사장")) {
			return "BUILDING_HOUSING";
		}
		if (containsAny(normalized, "noise", "odor", "pollution", "environment", "소음", "악취", "오염", "먼지", "환경")) {
			return "ENVIRONMENT";
		}
		if (containsAny(normalized, "toilet", "restroom", "sanitation", "food", "health", "화장실", "위생", "식품", "보건")) {
			return "HEALTH_SANITATION";
		}
		if (containsAny(normalized, "animal", "livestock", "pet", "동물", "가축", "반려견", "축산")) {
			return "ANIMAL_LIVESTOCK";
		}
		if (containsAny(normalized, "welfare", "disabled", "elderly", "복지", "장애인", "노인", "취약계층")) {
			return "WELFARE";
		}
		if (containsAny(normalized, "advertising", "banner", "urban", "현수막", "광고물", "도시", "개발행위")) {
			return "URBAN_MANAGEMENT";
		}
		if (containsAny(normalized, "safety", "hazard", "emergency", "위험", "안전", "재난", "화학", "위험물")) {
			return "SAFETY_CONTROL";
		}
		return "CIVIL_AFFAIRS";
	}

	private static boolean containsAny(String value, String... needles) {
		for (String needle : needles) {
			if (value.contains(needle)) {
				return true;
			}
		}
		return false;
	}
}
