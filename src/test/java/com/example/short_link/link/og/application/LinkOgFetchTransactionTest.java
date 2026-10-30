package com.example.short_link.link.og.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.application.properties.OgFetchProperties;
import com.example.short_link.link.application.write.LinkSidecars;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

class LinkOgFetchTransactionTest {
  @Test
  void aRefreshOpensNoOuterTransaction() {
    OgScraper scraper = mock(OgScraper.class);
    LinkRepository links = mock(LinkRepository.class);
    when(links.findByShortCode(any())).thenReturn(Optional.empty());
    var service =
        new LinkOgFetchService(
            scraper,
            links,
            mock(LinkSidecars.class),
            new SimpleMeterRegistry(),
            mock(LinkCacheEviction.class),
            mock(OgFetchProperties.class));
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    ProxyFactory factory = new ProxyFactory(service);
    factory.addAdvice(
        new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
    LinkOgFetchService proxy = (LinkOgFetchService) factory.getProxy();

    proxy.refresh(new ShortCode("sched01"), "https://example.com/scheduled");

    verifyNoInteractions(transactions);
  }
}
