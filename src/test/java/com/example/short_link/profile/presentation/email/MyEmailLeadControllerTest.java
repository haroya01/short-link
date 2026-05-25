package com.example.short_link.profile.presentation.email;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.profile.application.email.EmailLeadService;
import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.ProfileBlockType;
import com.example.short_link.profile.domain.repository.ProfileBlockRepository;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MyEmailLeadControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;
  @Autowired private ProfileBlockRepository blockRepository;
  @Autowired private EmailLeadService leadService;

  @Test
  void anonymousListIs401() throws Exception {
    mvc.perform(get("/api/v1/users/me/email-leads")).andExpect(status().isUnauthorized());
  }

  @Test
  void ownerListsTheirLeads() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-mel"));
    ProfileBlockEntity block =
        blockRepository.save(
            new ProfileBlockEntity(user.getId(), ProfileBlockType.EMAIL_FORM, "{}", 1));
    leadService.submit(user.getId(), block.getId(), "x@example.com", "1.2.3.4");
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(get("/api/v1/users/me/email-leads").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.total").isNumber());
  }

  @Test
  void exportCsvIncludesHeaderAndRows() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("c@x.com", "google", "g-cv"));
    ProfileBlockEntity block =
        blockRepository.save(
            new ProfileBlockEntity(user.getId(), ProfileBlockType.EMAIL_FORM, "{}", 1));
    leadService.submit(user.getId(), block.getId(), "csv@example.com", "1.2.3.4");
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(
            get("/api/v1/users/me/email-leads/export.csv")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(
            header().string("Content-Disposition", "attachment; filename=\"email-leads.csv\""))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("email,block_id")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("csv@example.com")));
  }

  @Test
  void exportCsvWithIncludeOptedOutContainsEvenOptedOut() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("co@x.com", "google", "g-cvo"));
    ProfileBlockEntity block =
        blockRepository.save(
            new ProfileBlockEntity(user.getId(), ProfileBlockType.EMAIL_FORM, "{}", 1));
    var lead = leadService.submit(user.getId(), block.getId(), "opt@example.com", "1.2.3.4");
    leadService.setOptedOut(user.getId(), lead.getId(), true);
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(
            get("/api/v1/users/me/email-leads/export.csv")
                .param("includeOptedOut", "true")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("opt@example.com")));
  }

  @Test
  void patchOptedOut() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("p@x.com", "google", "g-mep"));
    ProfileBlockEntity block =
        blockRepository.save(
            new ProfileBlockEntity(user.getId(), ProfileBlockType.EMAIL_FORM, "{}", 1));
    var lead = leadService.submit(user.getId(), block.getId(), "p@example.com", "1.2.3.4");
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(
            patch("/api/v1/users/me/email-leads/" + lead.getId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"optedOut\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.optedOut").value(true));
  }

  @Test
  void deleteRemovesLead() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("d@x.com", "google", "g-med"));
    ProfileBlockEntity block =
        blockRepository.save(
            new ProfileBlockEntity(user.getId(), ProfileBlockType.EMAIL_FORM, "{}", 1));
    var lead = leadService.submit(user.getId(), block.getId(), "d@example.com", "1.2.3.4");
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(
            delete("/api/v1/users/me/email-leads/" + lead.getId())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }
}
