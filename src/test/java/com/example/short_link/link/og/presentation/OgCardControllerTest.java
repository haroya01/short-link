package com.example.short_link.link.og.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.application.dto.CachedLinkFixture;
import com.example.short_link.link.application.read.LinkLookupQueryService;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.og.application.OgCardImageRenderer;
import com.example.short_link.link.stats.application.read.ClickTotalsReader;
import java.time.Instant;
import java.util.List;
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
class OgCardControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private LinkLookupQueryService lookup;
  @MockitoBean private OgCardImageRenderer renderer;
  @MockitoBean private ClickTotalsReader clickRepository;

  @Test
  void servesPngWithCacheHeaders() throws Exception {
    CachedLink cached =
        CachedLinkFixture.link(new LinkId(1L), "https://example.com").userId(7L).build();
    when(lookup.findActiveLink(eq(new ShortCode("abc1234")))).thenReturn(cached);
    when(clickRepository.countHumanByLinkId(eq(1L))).thenReturn(42L);
    byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G'};
    when(renderer.render(any(), any())).thenReturn(png);

    mvc.perform(get("/abc1234/og.png"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_PNG))
        .andExpect(header().string("Cache-Control", "public, max-age=60, s-maxage=60"))
        .andExpect(header().string("X-Robots-Tag", "noindex"));
  }

  @Test
  void drawsTheClickCountOnlyForALinkWhoseStatsArePublic() throws Exception {
    when(renderer.render(any(), any())).thenReturn(new byte[] {1});
    Instant later = Instant.now().plusSeconds(3600);
    serve("pub0001", fixture(1L).statsPublic(true).build());
    serve("prv0001", fixture(2L).build());
    serve("pwd0001", fixture(3L).statsPublic(true).passwordRequired(true).build());
    serve(
        "soon001",
        fixture(4L)
            .statsPublic(true)
            .visitOptions(new CachedLink.VisitOptions(false, null, later))
            .build());
    when(lookup.countHumanClicks(new LinkId(1L))).thenReturn(42L);

    for (String code : List.of("pub0001", "prv0001", "pwd0001", "soon001")) {
      mvc.perform(get("/" + code + "/og.png")).andExpect(status().isOk());
    }

    verify(renderer).render(endsWith("/pub0001"), eq(42L));
    verify(renderer).render(endsWith("/prv0001"), isNull());
    verify(renderer).render(endsWith("/pwd0001"), isNull());
    verify(renderer).render(endsWith("/soon001"), isNull());
    verify(lookup, never()).countHumanClicks(new LinkId(2L));
    verify(lookup, never()).countHumanClicks(new LinkId(3L));
    verify(lookup, never()).countHumanClicks(new LinkId(4L));
  }

  private static CachedLinkFixture fixture(long id) {
    return CachedLinkFixture.link(new LinkId(id), "https://example.com/" + id).userId(7L);
  }

  private void serve(String code, CachedLink link) {
    when(lookup.findActiveLink(eq(new ShortCode(code)))).thenReturn(link);
  }
}
