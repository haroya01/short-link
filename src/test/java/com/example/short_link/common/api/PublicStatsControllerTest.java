package com.example.short_link.common.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicStatsControllerTest {

  @Autowired private MockMvc mvc;

  @Test
  void returnsTotalsWithoutAuth() throws Exception {
    mvc.perform(get("/api/v1/public/stats"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.links").isNumber())
        .andExpect(jsonPath("$.clicks").isNumber());
  }

  @Test
  void healthEndpointAllowsDockerHealthcheckWithoutAuth() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }
}
