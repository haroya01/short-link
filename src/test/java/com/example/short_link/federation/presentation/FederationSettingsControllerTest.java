package com.example.short_link.federation.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.federation.application.FederationSettings;
import com.example.short_link.federation.application.FederationSettingsView;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = FederationSettingsController.class)
class FederationSettingsControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private FederationSettings settings;

  @Test
  void readsAndUpdatesTheSignedInUsersSettings() throws Exception {
    when(settings.view(7L)).thenReturn(new FederationSettingsView(true, false, "@yuki@kurl.me"));
    when(settings.update(7L, false, null))
        .thenReturn(new FederationSettingsView(false, false, "@yuki@kurl.me"));

    mvc.perform(
            get("/api/v1/federation/settings").header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(jsonPath("$.handle").value("@yuki@kurl.me"));
    mvc.perform(
            put("/api/v1/federation/settings")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false));
    mvc.perform(get("/api/v1/federation/settings")).andExpect(status().isUnauthorized());
  }
}
