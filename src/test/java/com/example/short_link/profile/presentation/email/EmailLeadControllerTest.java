package com.example.short_link.profile.presentation.email;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.profile.application.email.EmailLeadService;
import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.ProfileBlockType;
import com.example.short_link.profile.domain.repository.ProfileBlockRepository;
import com.example.short_link.profile.exception.EmailLeadRateLimitedException;
import com.example.short_link.profile.exception.InvalidUsernameException;
import com.example.short_link.profile.exception.ProfileNotFoundException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EmailLeadControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository userRepository;
  @Autowired private ProfileBlockRepository blockRepository;

  @MockitoBean private EmailLeadService leadService;

  @Test
  void submitAcceptedForEmailFormBlock() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("o@x.com", "google", "g-em"));
    ProfileBlockEntity block =
        blockRepository.save(
            new ProfileBlockEntity(
                user.getId(), ProfileBlockType.EMAIL_FORM, "{\"title\":\"newsletter\"}", 1));
    when(leadService.submitPublic(eq(block.getId()), eq("x@y.com"), anyString())).thenReturn(null);

    mvc.perform(
            post("/api/v1/public/email-leads")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blockId\":" + block.getId() + ",\"email\":\"x@y.com\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));
  }

  @Test
  void submitMissingBlockReturns404() throws Exception {
    doThrow(new ProfileNotFoundException("block 999999"))
        .when(leadService)
        .submitPublic(eq(999999L), eq("x@y.com"), anyString());

    mvc.perform(
            post("/api/v1/public/email-leads")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blockId\":999999,\"email\":\"x@y.com\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));
  }

  @Test
  void submitNonEmailFormBlockReturns404() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("o@x.com", "google", "g-em2"));
    ProfileBlockEntity block =
        blockRepository.save(
            new ProfileBlockEntity(user.getId(), ProfileBlockType.DIVIDER, null, 1));
    doThrow(new ProfileNotFoundException("block " + block.getId()))
        .when(leadService)
        .submitPublic(eq(block.getId()), eq("x@y.com"), anyString());

    mvc.perform(
            post("/api/v1/public/email-leads")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blockId\":" + block.getId() + ",\"email\":\"x@y.com\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void invalidEmailReturns400() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("o@x.com", "google", "g-em3"));
    ProfileBlockEntity block =
        blockRepository.save(
            new ProfileBlockEntity(
                user.getId(), ProfileBlockType.EMAIL_FORM, "{\"title\":\"newsletter\"}", 1));
    doThrow(new InvalidUsernameException("bad email"))
        .when(leadService)
        .submitPublic(eq(block.getId()), eq("bogus"), anyString());

    mvc.perform(
            post("/api/v1/public/email-leads")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blockId\":" + block.getId() + ",\"email\":\"bogus\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_USERNAME"));
  }

  @Test
  void rateLimitedReturns429() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("o@x.com", "google", "g-em4"));
    ProfileBlockEntity block =
        blockRepository.save(
            new ProfileBlockEntity(
                user.getId(), ProfileBlockType.EMAIL_FORM, "{\"title\":\"newsletter\"}", 1));
    doThrow(new EmailLeadRateLimitedException("too fast"))
        .when(leadService)
        .submitPublic(eq(block.getId()), eq("a@b.com"), anyString());

    mvc.perform(
            post("/api/v1/public/email-leads")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blockId\":" + block.getId() + ",\"email\":\"a@b.com\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("EMAIL_LEAD_RATE_LIMITED"));
  }

  @Test
  void usesForwardedForIpWhenPresent() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("o@x.com", "google", "g-em5"));
    ProfileBlockEntity block =
        blockRepository.save(
            new ProfileBlockEntity(
                user.getId(), ProfileBlockType.EMAIL_FORM, "{\"title\":\"x\"}", 1));

    mvc.perform(
            post("/api/v1/public/email-leads")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1")
                .content("{\"blockId\":" + block.getId() + ",\"email\":\"a@b.com\"}"))
        .andExpect(status().isOk());
  }
}
