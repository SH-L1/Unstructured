package egovframework.example.complaint.service;

import egovframework.example.complaint.api.dto.DepartmentResponse;
import java.util.List;

public interface DepartmentRoutingService {

	String route(String intent);

	List<DepartmentResponse> findDepartments();
}
