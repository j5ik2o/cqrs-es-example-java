package com.github.j5ik2o.cqrs.es.java.rmu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DatabaseSettingsTest {

  @Test
  void readsCredentialsAndUrlFromTheSecret() {
    var settings =
        DatabaseSettings.fromSecretJson(
            "{\"url\":\"jdbc:mysql://secret:3306/ceer\",\"username\":\"ceer\",\"password\":\"s3cret\"}",
            "jdbc:mysql://env:3306/ceer");

    assertEquals("jdbc:mysql://secret:3306/ceer", settings.url());
    assertEquals("ceer", settings.username());
    assertEquals("s3cret", settings.password());
  }

  @Test
  void fallsBackToTheEnvironmentUrlWhenTheSecretHasNoUrl() {
    var settings =
        DatabaseSettings.fromSecretJson(
            "{\"username\":\"ceer\",\"password\":\"s3cret\"}", "jdbc:mysql://env:3306/ceer");

    assertEquals("jdbc:mysql://env:3306/ceer", settings.url());
  }

  @Test
  void rejectsASecretWithoutAUsername() {
    var failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                DatabaseSettings.fromSecretJson(
                    "{\"password\":\"s3cret\"}", "jdbc:mysql://env:3306/ceer"));

    assertEquals("database secret has no username", failure.getMessage());
  }

  @Test
  void rejectsASecretWithoutAPassword() {
    var failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                DatabaseSettings.fromSecretJson(
                    "{\"username\":\"ceer\"}", "jdbc:mysql://env:3306/ceer"));

    assertEquals("database secret has no password", failure.getMessage());
  }

  @Test
  void rejectsASecretWithoutAUrlAndAFallback() {
    assertThrows(
        IllegalStateException.class,
        () ->
            DatabaseSettings.fromSecretJson(
                "{\"username\":\"ceer\",\"password\":\"s3cret\"}", null));
  }

  @Test
  void rejectsNonJsonSecretValues() {
    assertThrows(
        IllegalStateException.class,
        () -> DatabaseSettings.fromSecretJson("not-json", "jdbc:mysql://env:3306/ceer"));
  }

  @Test
  void keepsTheEnvironmentPathForLocalRuns() {
    var settings =
        DatabaseSettings.fromEnvironment("jdbc:mysql://localhost:3306/ceer", "root", "root");

    assertEquals("jdbc:mysql://localhost:3306/ceer", settings.url());
    assertEquals("root", settings.username());
    assertEquals("root", settings.password());
  }

  @Test
  void rejectsAnEnvironmentWithoutAUrl() {
    assertThrows(
        IllegalStateException.class, () -> DatabaseSettings.fromEnvironment(null, "root", "root"));
  }
}
