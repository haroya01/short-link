package com.example.short_link.billing.application.read;

import com.example.short_link.billing.application.StripeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Billing read-side queries. Currently surfaces only the configuration flag — subscription status /
 * invoice history projections land here when the Pro dashboard adds those views.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingQueryService {

  private final StripeProperties stripe;
}
