package com.example.short_link.federation.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.FederationInstanceActorEntity;
import com.example.short_link.testsupport.KurlWebMvcTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = InstanceActorController.class)
@Import({FederationUrls.class, InstanceActorControllerTest.Props.class})
class InstanceActorControllerTest {

  @TestConfiguration
  static class Props {
    @Bean
    FederationProperties federationProperties() {
      return new FederationProperties("https://kurl.me", "https://blog.kurl.me");
    }
  }

  @Autowired private MockMvc mvc;
  @MockitoBean private SigningKeys signingKeys;

  @Test
  void theInstanceIsAnApplicationActorWithItsKey() throws Exception {
    when(signingKeys.instanceActor())
        .thenReturn(new FederationInstanceActorEntity("-----BEGIN PUBLIC KEY-----\nI\n", "c"));

    mvc.perform(get("/ap/instance"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/activity+json"))
        .andExpect(jsonPath("$.id").value("https://kurl.me/ap/instance"))
        .andExpect(jsonPath("$.type").value("Application"))
        .andExpect(jsonPath("$.preferredUsername").value("kurl.me"))
        .andExpect(jsonPath("$.inbox").value("https://kurl.me/ap/inbox"))
        .andExpect(jsonPath("$.manuallyApprovesFollowers").value(true))
        .andExpect(jsonPath("$.publicKey.id").value("https://kurl.me/ap/instance#main-key"))
        .andExpect(jsonPath("$.publicKey.owner").value("https://kurl.me/ap/instance"));
  }
}
