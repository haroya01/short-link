package com.example.short_link.link.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.application.CachedLink;
import com.example.short_link.link.application.LinkLookupService;
import com.example.short_link.link.application.OgCardImageRenderer;
import com.example.short_link.link.domain.ClickEventRepository;
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

  @MockitoBean private LinkLookupService lookup;
  @MockitoBean private OgCardImageRenderer renderer;
  @MockitoBean private ClickEventRepository clickRepository;

  @Test
  void servesPngWithCacheHeaders() throws Exception {
    CachedLink cached =
        new CachedLink(1L, 7L, "https://example.com", null, null, null, null, null, List.of());
    when(lookup.findActiveLink(eq("abc1234"))).thenReturn(cached);
    when(clickRepository.countHumanByLinkId(eq(1L))).thenReturn(42L);
    byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G'};
    when(renderer.render(any(), anyLong())).thenReturn(png);

    mvc.perform(get("/abc1234/og.png"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_PNG))
        .andExpect(header().string("Cache-Control", "public, max-age=60, s-maxage=60"))
        .andExpect(header().string("X-Robots-Tag", "noindex"));
  }
}
