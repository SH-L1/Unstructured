package egovframework.example.complaint.service;

import egovframework.example.complaint.api.dto.DepartmentResponse;
import egovframework.example.complaint.domain.Department;
import egovframework.example.complaint.repository.DepartmentRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MockDepartmentRoutingService implements DepartmentRoutingService {

	private final DepartmentRepository departmentRepository;

	public MockDepartmentRoutingService(DepartmentRepository departmentRepository) {
		this.departmentRepository = departmentRepository;
	}

	@Override
	public String route(String intent) {
		return resolveDepartment(routeCode(intent)).getName();
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
		if (intent == null) {
			return "CIVIL_AFFAIRS";
		}
		String normalized = intent.toLowerCase();
		if (normalized.contains("waste") || normalized.contains("dumping") || normalized.contains("폐기물")
				|| normalized.contains("무단투기") || normalized.contains("쓰레기")) {
			return "RESOURCE_RECYCLING";
		}
		if (normalized.contains("road") || normalized.contains("street") || normalized.contains("도로")
				|| normalized.contains("포트홀") || normalized.contains("파손")) {
			return "ROAD";
		}
		if (normalized.contains("parking") || normalized.contains("traffic") || normalized.contains("주차")
				|| normalized.contains("주정차") || normalized.contains("교통") || normalized.contains("표지판")) {
			return "TRAFFIC";
		}
		return "CIVIL_AFFAIRS";
	}

	private Department resolveDepartment(String code) {
		return departmentRepository.findByCode(code)
				.orElseGet(() -> departmentRepository.findByCode("CIVIL_AFFAIRS")
						.orElseThrow(() -> new IllegalStateException("Department seed data is missing")));
	}
}
