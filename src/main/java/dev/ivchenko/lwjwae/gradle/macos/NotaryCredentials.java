package dev.ivchenko.lwjwae.gradle.macos;

import java.io.File;

/**
 * The ways to sign in to the notary service that a task was given, read from {@link
 * NotarizationExtension} when it runs: every component that the build script didn't set is {@code
 * null}.
 */
public record NotaryCredentials(
    String keychainProfile,
    File apiKey,
    String apiKeyId,
    String apiIssuer,
    String appleId,
    String teamId,
    String password) {
  /** Whether any way to sign in is set, which turns notarization on. */
  public boolean isSet() {
    return this.keychainProfile != null || this.apiKey != null || this.appleId != null;
  }
}
