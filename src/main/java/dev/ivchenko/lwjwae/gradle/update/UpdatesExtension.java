package dev.ivchenko.lwjwae.gradle.update;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code updates {}} block: where the application finds its new versions, and the key that
 * signs them.
 *
 * <pre>{@code
 * lwjwae {
 *     updates {
 *         manifestUrl = "https://example.com/notes/manifest.json"
 *         publicKey = "MCowBQYDK2VwAyEA..."
 *     }
 * }
 * }</pre>
 *
 * <p>With a manifest URL, the application carries both in {@code
 * META-INF/lwjwae/update.properties}, where {@code application.updater()} reads them. {@code
 * generateUpdateKeys} prints a new pair of keys once; the public one goes here, the private one
 * into a secret of CI, never into the repository.
 *
 * <p>A release then goes in three steps. {@code packageUpdate} puts the package of the platform of
 * the build into {@link #getDirectory()}, named after the platform, such as {@code
 * linux-x64.AppImage}: each platform builds its own. With the files of every platform in one
 * directory, {@code updateManifest} writes {@code manifest.json} next to them, with the version,
 * size, and SHA-256 of each, and signs it into {@code manifest.json.sig}. Uploading the directory
 * to the manifest URL publishes the release.
 */
public abstract class UpdatesExtension {
  /** The URL of {@code manifest.json}. Unset by default: the application has no updater. */
  public abstract Property<String> getManifestUrl();

  /** The Ed25519 key that signs the manifest, as Base64 of its X.509 encoding. */
  public abstract Property<String> getPublicKey();

  /**
   * The private key that signs the manifest, as Base64 of its PKCS #8 encoding. Default: the Gradle
   * property {@code lwjwae.updatePrivateKey}, then the environment variable {@code
   * LWJWAE_UPDATE_PRIVATE_KEY}, so it stays out of the build script.
   */
  public abstract Property<String> getPrivateKey();

  /** What changed, which the manifest carries to the application. Default: nothing. */
  public abstract Property<String> getNotes();

  /**
   * The oldest version that may keep running, which makes the update of an older one {@code
   * mandatory}. Unset by default.
   */
  public abstract Property<String> getMinimumVersion();

  /** Where the files of a release and the manifest go. Default: {@code build/lwjwae/update}. */
  public abstract DirectoryProperty getDirectory();
}
