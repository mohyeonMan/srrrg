package link.srrrg;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HomeControllerTest {

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new HomeController()).build();
	}

	@Test
	void rendersManagementConsoleWithOptionalCode() throws Exception {
		mvc.perform(get("/manage").param("code", "aB3x9Q"))
				.andExpect(status().isOk())
				.andExpect(view().name("management"))
				.andExpect(model().attribute("prefilledCode", "aB3x9Q"));
	}

	@Test
	void rendersManagementConsoleWithoutPrefilledCode() throws Exception {
		mvc.perform(get("/manage"))
				.andExpect(status().isOk())
				.andExpect(view().name("management"))
				.andExpect(model().attribute("prefilledCode", ""));
	}

	@Test
	void rendersUtmTestDestination() throws Exception {
		mvc.perform(get("/test/utm").param("any_parameter", "any value"))
				.andExpect(status().isOk())
				.andExpect(view().name("utm-test"));
	}
}
