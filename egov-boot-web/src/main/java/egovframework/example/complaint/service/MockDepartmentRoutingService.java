package egovframework.example.complaint.service;

import egovframework.example.complaint.api.dto.DepartmentResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MockDepartmentRoutingService implements DepartmentRoutingService {

	private static final String WASTE_DEPARTMENT = "Resource Recycling Division";
	private static final String ROAD_DEPARTMENT = "Road Management Division";
	private static final String CIVIL_DEPARTMENT = "Civil Affairs Division";

	@Override
	public String route(String intent) {
		if (intent == null) {
			return CIVIL_DEPARTMENT;
		}
		String normalized = intent.toLowerCase();
		if (normalized.contains("waste") || normalized.contains("dumping")) {
			return WASTE_DEPARTMENT;
		}
		if (normalized.contains("road") || normalized.contains("street")) {
			return ROAD_DEPARTMENT;
		}
		return CIVIL_DEPARTMENT;
	}

	@Override
	public List<DepartmentResponse> findDepartments() {
		return List.of(
				new DepartmentResponse("RESOURCE_RECYCLING", WASTE_DEPARTMENT, "Waste, dumping, recycling and cleanup complaints"),
				new DepartmentResponse("ROAD_MANAGEMENT", ROAD_DEPARTMENT, "Road damage, pavement, street light and safety complaints"),
				new DepartmentResponse("CIVIL_AFFAIRS", CIVIL_DEPARTMENT, "General civil complaint intake and routing")
		);
	}
}
