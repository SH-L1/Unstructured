package egovframework.example.complaint.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

	@GetMapping({"/dashboard", "/dashboard/"})
	public String dashboard() {
		return "forward:/dashboard/index.html";
	}
}
