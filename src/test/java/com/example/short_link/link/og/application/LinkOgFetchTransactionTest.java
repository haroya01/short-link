package com.example.short_link.link.og.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.application.properties.OgFetchProperties;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.og.domain.repository.LinkOgMetadataRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

class LinkOgFetchTransactionTest {
  @Test
  void onlyScheduledRefreshAddsAnOuterTransaction() {
    OgScraper scraper = mock(OgScraper.class);
    LinkRepository links = mock(LinkRepository.class);
    when(links.findByShortCode(any())).thenReturn(Optional.empty());
    var service =
        new LinkOgFetchService(
            scraper,
            links,
            mock(LinkOgMetadataRepository.class),
            new SimpleMeterRegistry(),
            mock(LinkCacheEviction.class),
            mock(OgFetchProperties.class));
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    ProxyFactory factory = new ProxyFactory(service);
    factory.addAdvice(
        new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
    LinkOgFetchService proxy = (LinkOgFetchService) factory.getProxy();

    proxy.fetchAfterCommit(new ShortCode("event01"), "https://example.com/event");
    verifyNoInteractions(transactions);

    proxy.refresh(new ShortCode("sched01"), "https://example.com/scheduled");
    verify(transactions).getTransaction(any());
    verify(transactions).commit(any());
  }
}
