package ascore.nodes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T5. The wrong role is refused in the filter chain, before the controller runs, so
 * no store is touched and nothing needs Mongo or Redis.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NodeSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void dashKeyCannotRegister() throws Exception {
		mockMvc.perform(post("/api/nodes/register")
						.header("X-Api-Key", "dev-dash-key")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nodeId\":\"n1\",\"hostname\":\"h\",\"maxConcurrentJobs\":1}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void nodeKeyCannotListNodes() throws Exception {
		mockMvc.perform(get("/api/nodes").header("X-Api-Key", "dev-node-key"))
				.andExpect(status().isForbidden());
	}

}
