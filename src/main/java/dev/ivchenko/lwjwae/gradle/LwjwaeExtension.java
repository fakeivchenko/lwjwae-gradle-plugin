package dev.ivchenko.lwjwae.gradle;

import dev.ivchenko.lwjwae.gradle.frontend.FrontendExtension;
import dev.ivchenko.lwjwae.gradle.macos.MacOsExtension;
import dev.ivchenko.lwjwae.gradle.update.UpdatesExtension;
import dev.ivchenko.lwjwae.gradle.windows.WindowsExtension;
import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

/**
 * The {@code lwjwae {}} block of a build script: which artifacts the plugin adds and at which
 * versions, and what goes into the native image.
 *
 * <p>Every property has a default. The versions default to the ones the plugin was released with,
 * so a plugin upgrade brings the matching library; a build script pins another when it needs to.
 *
 * <pre>{@code
 * lwjwae {
 *     displayName = "My app"
 *     vendor = "Example <hello@example.com>"
 *     jackson()
 *     packaging.all()
 * }
 * }</pre>
 *
 * <p>Each shortcut sets a property that a build script can also set on its own, with every other
 * setting of the platforms and the formats in {@link #getWindows()}, {@link #getMacos()}, and
 * {@link #getPackaging()}.
 */
public abstract class LwjwaeExtension {
  /** Where the icon is unless {@link #getIcon()} says otherwise, relative to the project. */
  public static final String DEFAULT_ICON = "src/main/icons/app.png";

  /** Whether to add the dependencies at all. Default: {@code true}. */
  public abstract Property<Boolean> getManaged();

  /** The version of {@code lwjwae-core} and the backends. Default: the one of the plugin. */
  public abstract Property<String> getVersion();

  /** The version of the codec modules. Default: the one of the plugin. */
  public abstract Property<String> getCodecsVersion();

  /** The backends on the runtime classpath. Default: {@link Backends#CURRENT_PLATFORM}. */
  public abstract Property<Backends> getBackends();

  /**
   * The backend that {@link Backends#CURRENT_PLATFORM} picks on Linux. Default: {@link
   * LinuxBackend#GTK3}. {@link Backends#ALL} brings both.
   */
  public abstract Property<LinuxBackend> getLinuxBackend();

  /** The codec behind the typed bridge. Default: {@link Codec#NONE}. */
  public abstract Property<Codec> getCodec();

  /**
   * The Jakarta JSON Binding implementation that comes with {@link Codec#JSONB}, as {@code
   * group:artifact}. Default: Eclipse Yasson, {@code org.eclipse:yasson}.
   */
  public abstract Property<String> getJsonbProvider();

  /** The version of {@link #getJsonbProvider()}. Default: the one the plugin was released with. */
  public abstract Property<String> getJsonbProviderVersion();

  /** The name of the executable. Default: the project name. */
  public abstract Property<String> getImageName();

  /**
   * The name of the application as people see it: in the menu of Linux, the properties dialog and
   * the installer of Windows, and the Dock and the menu bar of macOS. Default: the image name.
   */
  public abstract Property<String> getDisplayName();

  /**
   * Who publishes the application, as {@code Name} or {@code Name <email>}: the company of the
   * Windows version block and the manufacturer of the installer, without the address, and the
   * maintainer of the Debian package, which needs it. Unset by default.
   */
  public abstract Property<String> getVendor();

  /**
   * The application icon as one image: a square PNG file of 256 pixels or larger. The plugin
   * renders every size that a platform wants from it. On Windows, it becomes the {@code .ico} file
   * of the executable. Default: {@value #DEFAULT_ICON}, when the project has it.
   */
  public abstract RegularFileProperty getIcon();

  /**
   * Whether the files of the project's resources go into the native image, so that the page loads
   * from the executable as it does from the JAR file. The Native Build Tools plugin finds them in
   * the resource directories of the project and of the projects it depends on; the libraries bring
   * their own metadata. Default: on. Off leaves it to the {@code reachability-metadata.json} of the
   * project.
   */
  public abstract Property<Boolean> getEmbedResources();

  /**
   * {@code -Os}. A desktop application holds little live data, so size matters more than peak
   * throughput. Default: on.
   */
  public abstract Property<Boolean> getOptimizeForSize();

  /**
   * {@code -R:MaxHeapSize}, which keeps a long session from growing on garbage. Default: {@code
   * 64m}. An empty value leaves it unset.
   */
  public abstract Property<String> getMaxHeapSize();

  /**
   * Whether the GraalVM Native Build Tools plugin looks for a GraalVM through Gradle toolchains.
   * Default: off, so the build uses the JDK named by {@code GRAALVM_HOME}, which is what works on
   * every platform and in CI.
   */
  public abstract Property<Boolean> getToolchainDetection();

  /** More {@code native-image} arguments, appended after the ones of the plugin. */
  public abstract ListProperty<String> getBuildArgs();

  /** The Windows executable: subsystem, icon, version block. */
  @Nested
  public abstract WindowsExtension getWindows();

  /** The macOS executable: the embedded {@code Info.plist}. */
  @Nested
  public abstract MacOsExtension getMacos();

  /** The page that npm builds, if any. */
  @Nested
  public abstract FrontendExtension getFrontend();

  /** The installers and bundles around the executable. */
  @Nested
  public abstract PackagingExtension getPackaging();

  /** Where the application finds its updates. */
  @Nested
  public abstract UpdatesExtension getUpdates();

  /** Brings the Jackson codec, {@link Codec#JACKSON}. */
  public void jackson() {
    this.getCodec().set(Codec.JACKSON);
  }

  /** Brings the Gson codec, {@link Codec#GSON}. */
  public void gson() {
    this.getCodec().set(Codec.GSON);
  }

  /** Brings the JSON Binding codec with Eclipse Yasson, {@link Codec#JSONB}. */
  public void jsonb() {
    this.getCodec().set(Codec.JSONB);
  }

  /**
   * Brings the JSON Binding codec with another implementation.
   *
   * @param provider The implementation as {@code group:artifact:version}, such as {@code
   *     org.apache.johnzon:johnzon-jsonb:2.0.2}.
   */
  public void jsonb(String provider) {
    int versionAt = provider.lastIndexOf(':');
    if (versionAt < 0 || provider.indexOf(':') == versionAt) {
      throw new IllegalArgumentException("Expected group:artifact:version, got " + provider);
    }
    this.jsonb();
    this.getJsonbProvider().set(provider.substring(0, versionAt));
    this.getJsonbProviderVersion().set(provider.substring(versionAt + 1));
  }

  /** Configures {@link #getWindows()}. */
  public void windows(Action<? super WindowsExtension> action) {
    action.execute(this.getWindows());
  }

  /** Configures {@link #getMacos()}. */
  public void macos(Action<? super MacOsExtension> action) {
    action.execute(this.getMacos());
  }

  /** Configures {@link #getFrontend()}. */
  public void frontend(Action<? super FrontendExtension> action) {
    action.execute(this.getFrontend());
  }

  /** Configures {@link #getUpdates()}. */
  public void updates(Action<? super UpdatesExtension> action) {
    action.execute(this.getUpdates());
  }

  /** Configures {@link #getPackaging()}. */
  public void packaging(Action<? super PackagingExtension> action) {
    action.execute(this.getPackaging());
  }
}
