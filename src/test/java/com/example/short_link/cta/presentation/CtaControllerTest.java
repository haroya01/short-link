package com.example.short_link.cta.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.cta.application.read.CtaQueryService;
import com.example.short_link.cta.application.read.CtaView;
import com.example.short_link.cta.application.write.CreateCtaCommand;
import com.example.short_link.cta.application.write.CreateCtaUseCase;
import com.example.short_link.cta.application.write.DeleteCtaCommand;
import com.example.short_link.cta.application.write.DeleteCtaUseCase;
import com.example.short_link.cta.application.write.UpdateCtaCommand;
import com.example.short_link.cta.application.write.UpdateCtaUseCase;
import com.example.short_link.cta.domain.CtaEntity;
import com.example.short_link.cta.domain.CtaPurpose;
import com.example.short_link.cta.domain.CtaStyle;
import com.example.short_link.cta.exception.CtaErrorCode;
import com.example.short_link.cta.exception.CtaException;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = CtaController.class)
@Import(CtaExceptionHandler.class)
class CtaControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private CreateCtaUseCase createCta;
  @MockitoBean private UpdateCtaUseCase updateCta;
  @MockitoBean private DeleteCtaUseCase deleteCta;
  @MockitoBean private CtaQueryService ctaQueryService;

  private static final long USER_ID = 7L;

  @Test
  void anonymousCreateIs401() throws Exception {
    mvc.perform(
            post("/api/v1/ctas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"L\",\"url\":\"https://x\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createReturns201() throws Exception {
    CtaEntity saved =
        new CtaEntity(
            USER_ID, "30 min", "https://cal.com/me", CtaStyle.PRIMARY, CtaPurpose.BOOKING);
    when(createCta.execute(any(CreateCtaCommand.class))).thenReturn(saved);

    mvc.perform(
            post("/api/v1/ctas")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"label\":\"30 min\",\"url\":\"https://cal.com/me\",\"style\":\"PRIMARY\",\"purpose\":\"BOOKING\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.label").value("30 min"))
        .andExpect(jsonPath("$.purpose").value("BOOKING"));
  }

  @Test
  void invalidCreateRequestReturns400() throws Exception {
    mvc.perform(
            post("/api/v1/ctas")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"\",\"url\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listReturnsViews() throws Exception {
    CtaView v1 =
        CtaView.from(
            new CtaEntity(USER_ID, "L1", "https://1", CtaStyle.PRIMARY, CtaPurpose.BOOKING));
    CtaView v2 =
        CtaView.from(
            new CtaEntity(USER_ID, "L2", "https://2", CtaStyle.SECONDARY, CtaPurpose.SUBSCRIBE));
    when(ctaQueryService.listMyCtas(USER_ID)).thenReturn(List.of(v1, v2));

    mvc.perform(get("/api/v1/ctas").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].label").value("L1"))
        .andExpect(jsonPath("$[1].label").value("L2"));
  }

  @Test
  void findByIdReturnsView() throws Exception {
    CtaView view =
        CtaView.from(new CtaEntity(USER_ID, "L", "https://x", CtaStyle.PRIMARY, CtaPurpose.CUSTOM));
    when(ctaQueryService.findOwnCta(USER_ID, 42L)).thenReturn(view);

    mvc.perform(get("/api/v1/ctas/42").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.label").value("L"));
  }

  @Test
  void findByIdNotFoundReturns404() throws Exception {
    when(ctaQueryService.findOwnCta(USER_ID, 99L))
        .thenThrow(new CtaException(CtaErrorCode.CTA_NOT_FOUND, 99L));

    mvc.perform(get("/api/v1/ctas/99").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CTA_NOT_FOUND"));
  }

  @Test
  void patchUpdatesLabel() throws Exception {
    CtaEntity updated =
        new CtaEntity(USER_ID, "New Label", "https://x", CtaStyle.PRIMARY, CtaPurpose.CUSTOM);
    when(updateCta.execute(any(UpdateCtaCommand.class))).thenReturn(updated);

    mvc.perform(
            patch("/api/v1/ctas/42")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"New Label\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.label").value("New Label"));
  }

  @Test
  void deleteReturns204() throws Exception {
    mvc.perform(delete("/api/v1/ctas/42").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isNoContent());

    verify(deleteCta).execute(any(DeleteCtaCommand.class));
  }
}
