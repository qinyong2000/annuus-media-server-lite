package com.ams.io;

public class ReadBlockingException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  @Override
  public synchronized Throwable fillInStackTrace() {
      return this;
  }
}