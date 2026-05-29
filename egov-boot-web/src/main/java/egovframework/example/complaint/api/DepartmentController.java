package egovframework.example.complaint.api;

import egovframework.example.complaint.api.dto.ApiResponse;
import egovframework.example.complaint.api.dto.DepartmentResponse;
import egovframework.example.complaint.service.DepartmentRoutingService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

	private final DepartmentRoutingService departmentRoutingService;

	public DepartmentController(DepartmentRoutingService departmentRoutingService) {
		this.departmentRoutingService = departmentRoutingService;
	}

	@GetMapping
	public ApiResponse<List<DepartmentResponse>> findAll() {
		return ApiResponse.ok(departmentRoutingService.findDepartments());
	}
}
