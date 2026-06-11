package egovframework.example.complaint.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import egovframework.example.EgovBootApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;

@SpringBootTest(
		classes = EgovBootApplication.class,
		properties = {
				"app.security.session.enabled=true",
				"app.security.session.intake-password=intake-test",
				"app.security.session.reviewer-password=reviewer-test",
				"app.security.session.approver-password=approver-test",
				"app.security.session.knowledge-admin-password=knowledge-test",
				"app.security.session.auditor-password=auditor-test",
				"app.security.session.admin-password=admin-test"
		}
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityBoundaryTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void protectsReviewWorkspaceAndActuator() throws Exception {
		mockMvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection());
		mockMvc.perform(get("/login")).andExpect(status().isOk());
		mockMvc.perform(get("/actuator/health")).andExpect(status().is3xxRedirection());
		MockHttpSession intakeSession = login("intake", "intake-test");
		mockMvc.perform(get("/actuator/health").session(intakeSession))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/complaints").session(intakeSession))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/actuator/health").session(login("auditor", "auditor-test")))
				.andExpect(status().isOk());
		mockMvc.perform(get("/dashboard/").session(login("reviewer", "reviewer-test")))
				.andExpect(status().isOk());
	}

	@Test
	void disablesEgovSampleCrud() throws Exception {
		mockMvc.perform(get("/egovSampleList.do").session(login("admin", "admin-test")))
				.andExpect(status().isNotFound());
	}

	private MockHttpSession login(String username, String password) throws Exception {
		MvcResult result = mockMvc.perform(formLogin().user(username).password(password))
				.andExpect(status().is3xxRedirection())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}
}
