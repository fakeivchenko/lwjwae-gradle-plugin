package dev.ivchenko.lwjwae.gradle.windows;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code windows { signing {} }} block: the code signing certificate that signs the executable
 * and the installer, so that Windows names the publisher and SmartScreen doesn't warn about an
 * unknown one.
 *
 * <pre>{@code
 * lwjwae {
 *     windows {
 *         signing {
 *             certificateThumbprint = "0123456789ABCDEF0123456789ABCDEF01234567"
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p>The certificate is either a {@code .pfx} file with its password, or one in the certificate
 * store of the user, named by its thumbprint: a certificate authority issues a code signing
 * certificate only on a hardware token or in a cloud HSM since 2023, and the driver of the token
 * puts the certificate into the store. Signing is off until one of the two is set. {@code signtool}
 * of the Windows SDK signs, with SHA-256 and a timestamp, so the signature outlives the
 * certificate.
 */
public abstract class WindowsSigningExtension {
  /** The certificate and its private key as a {@code .pfx} file. Unset by default. */
  public abstract RegularFileProperty getCertificateFile();

  /**
   * The password of {@link #getCertificateFile()}. Default: the Gradle property {@code
   * lwjwae.windowsCertificatePassword}, then the environment variable {@code
   * LWJWAE_WINDOWS_CERTIFICATE_PASSWORD}, so it stays out of the build script.
   */
  public abstract Property<String> getCertificatePassword();

  /**
   * The SHA-1 thumbprint of a certificate in the store of the user, in hexadecimal, instead of a
   * file. Unset by default.
   */
  public abstract Property<String> getCertificateThumbprint();

  /**
   * The RFC 3161 time stamp server, which vouches for the time of the signature. Default: {@value
   * #DEFAULT_TIMESTAMP_URL}.
   */
  public abstract Property<String> getTimestampUrl();

  /** The time stamp server that signs by default. */
  public static final String DEFAULT_TIMESTAMP_URL = "http://timestamp.digicert.com";
}
