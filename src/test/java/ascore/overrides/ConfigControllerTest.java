package ascore.overrides;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import ascore.egress.EgressService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The schema, the ETag and the roles, over HTTP. The store and cache are the in-memory ones and egress is
 * mocked, so this runs without Mongo, Redis or Roblox.
 *
 * Each test uses its own placeId because the context, and so the store, is shared.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConfigControllerTest {


	@TestConfiguration
	static class InMemory {

		@Bean
		@Primary
		ConfigStore inMemoryConfigStore() {return new InMemoryConfigStore();}

		@Bean
		@Primary
		ActiveConfigCache inMemoryActiveConfigCache() {return new InMemoryActiveConfigCache();}
	}


	private static final String DASH = "dev-dash-key";
	private static final String ROBLOX = "dev-roblox-key";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private EgressService egress;

	private ResultActions save(String placeId, String values) throws Exception {
		return mockMvc.perform(put("/api/config")
				.header("X-Api-Key", DASH)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"placeId\":\"" + placeId + "\",\"namespace\":\"spawns\",\"values\":" + values + "}"));
	}

	private void activate(String placeId, int version) throws Exception {
		mockMvc.perform(post("/api/config/activate/" + version)
						.header("X-Api-Key", DASH)
						.param("placeId", placeId)
						.param("namespace", "spawns"))
				.andExpect(status().isOk());
	}

	private String etag(String placeId) throws Exception {
		return mockMvc.perform(get("/api/config/active").header("X-Api-Key", ROBLOX).param("placeId", placeId))
				.andExpect(status().isOk())
				.andReturn().getResponse().getHeader(HttpHeaders.ETAG);
	}

	@Test
	void typoIs400NamingTheKey() throws Exception {
		save("t2", "{\"zombeSpeed\":16}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.zombeSpeed").exists());
	}

	@Test
	void unchangedConfigIs304AndActivationChangesTheEtag() throws Exception {
		save("t4", "{\"zombieSpeed\":16}").andExpect(jsonPath("$.version").value(1));
		save("t4", "{\"zombieSpeed\":20}").andExpect(jsonPath("$.version").value(2));
		activate("t4", 1);

		String first = etag("t4");
		mockMvc.perform(get("/api/config/active").header("X-Api-Key", ROBLOX).param("placeId", "t4")
						.header(HttpHeaders.IF_NONE_MATCH, first))
				.andExpect(status().isNotModified())
				.andExpect(content().string(""));

		activate("t4", 2);
		mockMvc.perform(get("/api/config/active").header("X-Api-Key", ROBLOX).param("placeId", "t4")
						.header(HttpHeaders.IF_NONE_MATCH, first))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ETAG, not(first)))
				.andExpect(content().string(containsString("20")));
	}

	@Test
	void robloxCannotSave() throws Exception {
		mockMvc.perform(put("/api/config")
						.header("X-Api-Key", ROBLOX)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"namespace\":\"spawns\",\"values\":{}}"))
				.andExpect(status().isForbidden());
	}

	// default deny: the dashboard reads history, not the ROBLOX poll path
	@Test
	void dashCannotPollActive() throws Exception {
		mockMvc.perform(get("/api/config/active").header("X-Api-Key", DASH)).andExpect(status().isForbidden());
	}

	@Test
	void robloxCannotReadHistory() throws Exception {
		mockMvc.perform(get("/api/config/history").header("X-Api-Key", ROBLOX)).andExpect(status().isForbidden());
	}

}
