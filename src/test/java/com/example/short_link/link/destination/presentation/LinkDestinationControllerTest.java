package com.example.short_link.link.destination.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.destination.application.dto.DestinationSummary;
import com.example.short_link.link.destination.application.read.LinkDestinationQueryService;
import com.example.short_link.link.destination.application.write.AddDestinationUseCase;
import com.example.short_link.link.destination.application.write.DeleteDestinationUseCase;
import com.example.short_link.link.destination.application.write.SetBlockedCountriesUseCase;
import com.example.short_link.link.destination.application.write.UpdateDestinationUseCase;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
class LinkDestinationControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;

  @MockitoBean private AddDestinationUseCase addUseCase;
  @MockitoBean private UpdateDestinationUseCase updateUseCase;
  @MockitoBean private DeleteDestinationUseCase deleteUseCase;
  @MockitoBean private SetBlockedCountriesUseCase setBlockedUseCase;

  @MockitoBean private LinkDestinationQueryService query;

  private static DestinationSummary sample() {
    return new DestinationSummary(
        1L, "https://example.com", 100, "main", true, null, null, null, Instant.EPOCH);
  }

  @Test
  void anonymousListIs401() throws Exception {
    mvc.perform(get("/api/v1/links/abc1234/destinations")).andExpect(status().isUnauthorized());
  }

  @Test
  void listReturnsDestinations() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("d@x.com", "google", "g-dst"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(query.list(eq(user.getId()), eq(new ShortCode("abc1234")))).thenReturn(List.of(sample()));

    mvc.perform(
            get("/api/v1/links/abc1234/destinations").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].url").value("https://example.com"));
  }

  @Test
  void addCreatesAndReturns201() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("a@x.com", "google", "g-add"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(addUseCase.execute(
            eq(user.getId()),
            eq(new ShortCode("abc1234")),
            anyString(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenReturn(sample());

    mvc.perform(
            post("/api/v1/links/abc1234/destinations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\",\"weight\":100,\"label\":\"main\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.url").value("https://example.com"));
  }

  @Test
  void updateReturnsOk() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-dup"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(updateUseCase.execute(
            eq(user.getId()),
            eq(new ShortCode("abc1234")),
            eq(1L),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenReturn(sample());

    mvc.perform(
            patch("/api/v1/links/abc1234/destinations/1")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"renamed\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void deleteReturns204() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("d2@x.com", "google", "g-ddel"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    doNothing().when(deleteUseCase).execute(anyLong(), any(ShortCode.class), anyLong());

    mvc.perform(
            delete("/api/v1/links/abc1234/destinations/1")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
  }

  @Test
  void setBlockedCountriesReturnsCsv() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("b@x.com", "google", "g-blk"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    LinkEntity link = Mockito.mock(LinkEntity.class);
    when(link.getBlockedCountries()).thenReturn("KP,IR");
    when(setBlockedUseCase.execute(eq(user.getId()), eq(new ShortCode("abc1234")), eq("KP,IR")))
        .thenReturn(link);

    mvc.perform(
            put("/api/v1/links/abc1234/blocked-countries")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codes\":\"KP,IR\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.codes").value("KP,IR"));
  }
}
