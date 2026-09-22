package ascore.jobs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// roles. Refused in the filter chain, so no store is touched
@SpringBootTest
@AutoConfigureMockMvc
class JobSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void dashKeyCannotClaim() throws Exception {
		mockMvc.perform(post("/api/jobs/claim")
						.header("X-Api-Key", "dev-dash-key")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nodeId\":\"n1\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void nodeKeyCannotCreate() throws Exception {
		mockMvc.perform(post("/api/jobs")
						.header("X-Api-Key", "dev-node-key")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"type\":\"CUSTOM\",\"mapId\":\"m\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void nodeKeyCannotList() throws Exception {
		mockMvc.perform(get("/api/jobs").header("X-Api-Key", "dev-node-key")).andExpect(status().isForbidden());
	}

}
