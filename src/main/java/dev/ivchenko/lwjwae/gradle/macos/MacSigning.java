package dev.ivchenko.lwjwae.gradle.macos;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

/**
 * The inputs of a task that signs and notarizes a macOS package: the bundle and the disk image take
 * the same ones. The ways to sign in to the notary service and the password stay out of the inputs:
 * a signature isn't cached anyway, and a secret has no place in a build cache or a build scan.
 */
public interface MacSigning {
  /** The {@code codesign} identity, or nothing for an unsigned package. */
  @Input
  @Optional
  Property<String> getSigningIdentity();

  /** Whether {@code codesign} turns on the hardened runtime, which notarization requires. */
  @Input
  Property<Boolean> getHardenedRuntime();

  /** The entitlements that {@code codesign} gives the executable, or nothing. */
  @InputFile
  @Optional
  @PathSensitive(PathSensitivity.NONE)
  RegularFileProperty getEntitlements();

  /** The keychain profile of {@code notarytool}, or nothing. */
  @Internal
  Property<String> getKeychainProfile();

  /** The private key of an API key of App Store Connect, or nothing. */
  @Internal
  RegularFileProperty getApiKey();

  /** The ID of the API key. */
  @Internal
  Property<String> getApiKeyId();

  /** The issuer of the API key. */
  @Internal
  Property<String> getApiIssuer();

  /** The Apple ID that signs in, or nothing. */
  @Internal
  Property<String> getAppleId();

  /** The team of the Apple ID. */
  @Internal
  Property<String> getTeamId();

  /** The app-specific password of the Apple ID. */
  @Internal
  Property<String> getPassword();

  /** The ways to sign in to the notary service that this task was given. */
  default NotaryCredentials notaryCredentials() {
    return new NotaryCredentials(
        this.getKeychainProfile().getOrNull(),
        this.getApiKey().isPresent() ? this.getApiKey().get().getAsFile() : null,
        this.getApiKeyId().getOrNull(),
        this.getApiIssuer().getOrNull(),
        this.getAppleId().getOrNull(),
        this.getTeamId().getOrNull(),
        this.getPassword().getOrNull());
  }

  /** Takes the signing and the notarization of {@code macos} into {@code task}. */
  static void wire(MacSigning task, MacOsExtension macos) {
    NotarizationExtension notarization = macos.getNotarization();
    task.getSigningIdentity().set(macos.getSigningIdentity());
    task.getHardenedRuntime().set(macos.getHardenedRuntime());
    task.getEntitlements().set(macos.getEntitlements());
    task.getKeychainProfile().set(notarization.getKeychainProfile());
    task.getApiKey().set(notarization.getApiKey());
    task.getApiKeyId().set(notarization.getApiKeyId());
    task.getApiIssuer().set(notarization.getApiIssuer());
    task.getAppleId().set(notarization.getAppleId());
    task.getTeamId().set(notarization.getTeamId());
    task.getPassword().set(notarization.getPassword());
  }
}
