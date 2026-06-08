package egovframework.example.complaint.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

	@GetMapping({"/", "/dashboard/"})
	public String dashboard(HttpServletRequest request) {
		CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
		if (csrfToken != null) {
			csrfToken.getToken();
		}
		return "forward:/dashboard/index.html";
	}

	@GetMapping("/dashboard")
	public String dashboardWithoutTrailingSlash() {
		return "redirect:/dashboard/";
	}
}
