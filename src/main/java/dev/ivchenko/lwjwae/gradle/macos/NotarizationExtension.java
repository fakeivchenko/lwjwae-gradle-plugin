package dev.ivchenko.lwjwae.gradle.macos;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code macos { notarization {} }} block: how the bundle and the disk image reach the notary
 * service of Apple, which checks them and issues a ticket, so that Gatekeeper opens a downloaded
 * application without a warning.
 *
 * <pre>{@code
 * lwjwae {
 *     macos {
 *         signingIdentity = "Developer ID Application: Example (TEAMID1234)"
 *         notarization {
 *             keychainProfile = "notary"
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p>Notarization is off until one of three ways to sign in is set, and it needs a {@code
 * signingIdentity} of a Developer ID: a keychain profile that {@code xcrun notarytool
 * store-credentials} saved, which suits a Mac of a developer; an API key of App Store Connect,
 * which suits CI; or an Apple ID with an app-specific password. {@code xcrun notarytool submit
 * --wait} sends the package and waits for the verdict, and {@code xcrun stapler} staples the ticket
 * to it, so that it opens without a network.
 */
public abstract class NotarizationExtension {
  /** The name of a profile of {@code notarytool} in the keychain. Unset by default. */
  public abstract Property<String> getKeychainProfile();

  /** The private key of an API key of App Store Connect, an {@code AuthKey_ID.p8} file. */
  public abstract RegularFileProperty getApiKey();

  /** The ID of {@link #getApiKey()}. */
  public abstract Property<String> getApiKeyId();

  /** The issuer of {@link #getApiKey()}, a UUID that App Store Connect shows. */
  public abstract Property<String> getApiIssuer();

  /** The Apple ID of a developer, an email address. */
  public abstract Property<String> getAppleId();

  /** The team of {@link #getAppleId()}. */
  public abstract Property<String> getTeamId();

  /**
   * The app-specific password of {@link #getAppleId()}. Default: the Gradle property {@code
   * lwjwae.notaryPassword}, then the environment variable {@code LWJWAE_NOTARY_PASSWORD}, so it
   * stays out of the build script.
   */
  public abstract Property<String> getPassword();
}
