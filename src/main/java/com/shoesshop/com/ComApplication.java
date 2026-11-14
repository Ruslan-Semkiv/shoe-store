package com.shoesshop.com;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ComApplication {

  public static void main(String[] args) {
    // The Postgres driver sends the JVM's zone to the server on connect. Machines that still
    // report the old "Europe/Kiev" name are refused by servers whose tzdata dropped it, so the
    // app runs in UTC everywhere (as in the Docker image). Timestamps are Instants either way.
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    SpringApplication.run(ComApplication.class, args);
  }

}
