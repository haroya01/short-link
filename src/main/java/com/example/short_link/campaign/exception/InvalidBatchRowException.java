package com.example.short_link.campaign.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidBatchRowException extends RuntimeException {
  public InvalidBatchRowException(int rowIndex, String reason) {
    super("row " + rowIndex + ": " + reason);
  }
}
