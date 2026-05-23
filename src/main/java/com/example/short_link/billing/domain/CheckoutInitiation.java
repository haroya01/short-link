package com.example.short_link.billing.domain;

public record CheckoutInitiation(String checkoutUrl, String newlyCreatedCustomerId) {

  public boolean hasNewCustomer() {
    return newlyCreatedCustomerId != null;
  }
}
