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
		if (normalized.contains("waste") || normalized.contains("dumping")) {
			return "RESOURCE_RECYCLING";
		}
		if (normalized.contains("road") || normalized.contains("street")) {
			return "ROAD";
		}
		return "CIVIL_AFFAIRS";
	}

	private Department resolveDepartment(String code) {
		return departmentRepository.findByCode(code)
				.orElseGet(() -> departmentRepository.findByCode("CIVIL_AFFAIRS")
						.orElseThrow(() -> new IllegalStateException("Department seed data is missing")));
	}
}
