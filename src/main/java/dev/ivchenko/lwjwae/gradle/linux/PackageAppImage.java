package dev.ivchenko.lwjwae.gradle.linux;

import dev.ivchenko.lwjwae.gradle.util.Downloads;
import dev.ivchenko.lwjwae.gradle.util.Icons;
import dev.ivchenko.lwjwae.gradle.util.Platform;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import javax.inject.Inject;
import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.work.DisableCachingByDefault;

/**
 * Builds an AppImage around the native executable with {@code appimagetool}.
 *
 * <p>The task lays out an {@code AppDir}: the executable under {@code usr/bin}, {@code AppRun} as a
 * link to it, the desktop entry, and the icon at 256 pixels, which is also the {@code .DirIcon};
 * without an icon of the application, a placeholder with its initial. {@code appimagetool} then
 * packs it into a squashfs image behind the AppImage runtime. The tool itself is an AppImage; the
 * task downloads the release of the tool and of the runtime that the plugin knows, checks their
 * SHA-256, and extracts the tool into the Gradle cache, because running it extracted needs no FUSE,
 * which containers and CI runners usually lack.
 */
@DisableCachingByDefault(because = "Packs an executable that is already a build output")
public abstract class PackageAppImage extends DefaultTask {
  /** The release of {@code appimagetool} that the plugin downloads. */
  public static final String TOOL_VERSION = "1.9.1";

  /**
   * The release of the AppImage runtime that goes in front of the image. {@code appimagetool} would
   * otherwise download the newest build of the runtime on every run, unpinned and unchecked.
   */
  public static final String RUNTIME_VERSION = "20251108";

  private static final String TOOL_URL =
      "https://github.com/AppImage/appimagetool/releases/download/"
          + TOOL_VERSION
          + "/appimagetool-%s.AppImage";

  private static final String RUNTIME_URL =
      "https://github.com/AppImage/type2-runtime/releases/download/"
          + RUNTIME_VERSION
          + "/runtime-%s";

  /** The SHA-256 of each {@code appimagetool} release file, by architecture. */
  private static final Map<String, String> TOOL_CHECKSUMS =
      Map.of(
          "x86_64", "ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0",
          "aarch64", "f0837e7448a0c1e4e650a93bb3e85802546e60654ef287576f46c71c126a9158");

  /** The SHA-256 of each runtime release file, by architecture. */
  private static final Map<String, String> RUNTIME_CHECKSUMS =
      Map.of(
          "x86_64", "2fca8b443c92510f1483a883f60061ad09b46b978b2631c807cd873a47ec260d",
          "aarch64", "00cbdfcf917cc6c0ff6d3347d59e0ca1f7f45a6df1a428a0d6d8a78664d87444");

  /** The native executable. */
  @InputFile
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getExecutable();

  /** The application icon, or nothing. */
  @InputFile
  @Optional
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getIcon();

  /**
   * The name of the icon in the icon theme, unique to the application, such as {@code
   * com.example.notes}: an icon theme may have a generic icon named after the executable, such as
   * {@code notes} in Breeze, and it would win over the one of the application.
   */
  @Input
  public abstract Property<String> getIconName();

  /** The name shown in the menu. */
  @Input
  public abstract Property<String> getDisplayName();

  /** The name of the executable inside the image, and of the desktop entry. */
  @Input
  public abstract Property<String> getImageName();

  /** The one-line description. */
  @Input
  public abstract Property<String> getSummary();

  /** The desktop entry categories. */
  @Input
  public abstract ListProperty<String> getCategories();

  /** A tool to use instead of the downloaded one. */
  @InputFile
  @Optional
  @PathSensitive(PathSensitivity.ABSOLUTE)
  public abstract RegularFileProperty getTool();

  /** Where the downloaded tool lives between builds. */
  @Internal
  public abstract DirectoryProperty getCacheDirectory();

  /** The {@code AppDir} to lay out. */
  @Internal
  public abstract DirectoryProperty getAppDir();

  /** The AppImage to write. */
  @OutputFile
  public abstract RegularFileProperty getAppImage();

  /** Gradle's process runner. */
  @Inject
  protected abstract ExecOperations getExecOperations();

  /** Gradle's file operations. */
  @Inject
  protected abstract FileSystemOperations getFileSystemOperations();

  /** Lays the {@code AppDir} out and packs it. */
  @TaskAction
  @SneakyThrows
  public void build() {
    String name = this.getImageName().get();
    Path appDir = this.getAppDir().get().getAsFile().toPath();
    this.getFileSystemOperations().delete(spec -> spec.delete(appDir));
    Path bin = Files.createDirectories(appDir.resolve("usr/bin"));
    Path program = bin.resolve(name);
    Files.copy(
        this.getExecutable().get().getAsFile().toPath(),
        program,
        StandardCopyOption.REPLACE_EXISTING);
    program.toFile().setExecutable(true, false);
    Files.createSymbolicLink(appDir.resolve("AppRun"), Path.of("usr/bin/" + name));
    Files.writeString(
        appDir.resolve(name + ".desktop"),
        DesktopEntry.render(
            this.getDisplayName().get(),
            this.getSummary().get(),
            name,
            this.getIconName().get(),
            this.getCategories().get()),
        StandardCharsets.UTF_8);
    // appimagetool refuses an AppDir without the icon that the desktop entry names.
    byte[] png =
        Icons.png(
            this.getIcon().isPresent()
                ? Icons.scale(Icons.read(this.getIcon().get().getAsFile()), 256)
                : Icons.placeholder(this.getDisplayName().get()));
    Files.write(appDir.resolve(this.getIconName().get() + ".png"), png);
    Files.write(appDir.resolve(".DirIcon"), png);

    File output = this.getAppImage().get().getAsFile();
    Files.createDirectories(output.getParentFile().toPath());
    Files.deleteIfExists(output.toPath());
    String tool =
        this.getTool().isPresent()
            ? this.getTool().get().getAsFile().getAbsolutePath()
            : this.tool();
    Path runtime = this.runtime();
    this.getExecOperations()
        .exec(
            spec -> {
              spec.setExecutable(tool);
              spec.args(
                  "--no-appstream",
                  "--runtime-file",
                  runtime.toString(),
                  appDir.toString(),
                  output.getAbsolutePath());
              spec.environment("ARCH", PackageAppImage.architecture());
            });
  }

  /**
   * Returns the {@code AppRun} of the extracted tool in the cache, downloading and extracting it on
   * first use.
   */
  @SneakyThrows
  private String tool() {
    String arch = PackageAppImage.architecture();
    Path cache = this.cache().resolve("appimagetool-" + TOOL_VERSION + "-" + arch);
    Path root = cache.resolve("squashfs-root");
    Path appRun = root.resolve("AppRun");
    if (Files.isExecutable(appRun)) {
      return appRun.toString();
    }
    this.getLogger().lifecycle("Downloading appimagetool {} for {}", TOOL_VERSION, arch);
    Path download =
        Downloads.verified(
            TOOL_URL.formatted(arch),
            PackageAppImage.checksum(TOOL_CHECKSUMS, arch),
            cache.resolve("appimagetool.AppImage"));
    download.toFile().setExecutable(true, false);
    // Extracted aside and moved in whole: an extraction cut short leaves no AppRun behind, so the
    // next build extracts again instead of running a partial tool.
    Path extraction = Files.createTempDirectory(cache, "extract-");
    try {
      this.getExecOperations()
          .exec(
              spec -> {
                spec.setExecutable(download.toString());
                spec.args("--appimage-extract");
                spec.setWorkingDir(extraction.toFile());
              });
      if (!Files.isExecutable(appRun)) {
        this.getFileSystemOperations().delete(spec -> spec.delete(root));
        Files.move(extraction.resolve("squashfs-root"), root, StandardCopyOption.ATOMIC_MOVE);
      }
    } finally {
      this.getFileSystemOperations().delete(spec -> spec.delete(extraction));
    }
    return appRun.toString();
  }

  /** Returns the runtime in the cache, downloading it on first use. */
  private Path runtime() {
    String arch = PackageAppImage.architecture();
    return Downloads.verified(
        RUNTIME_URL.formatted(arch),
        PackageAppImage.checksum(RUNTIME_CHECKSUMS, arch),
        this.cache().resolve("appimage-runtime-" + RUNTIME_VERSION + "-" + arch));
  }

  private Path cache() {
    return this.getCacheDirectory().get().getAsFile().toPath();
  }

  private static String checksum(Map<String, String> checksums, String arch) {
    String checksum = checksums.get(arch);
    if (checksum == null) {
      throw new GradleException("No AppImage tools for " + arch + "; set packaging.appImage.tool");
    }
    return checksum;
  }

  /** The AppImage name of the architecture that runs the build. */
  public static String architecture() {
    return Platform.isArm64() ? "aarch64" : "x86_64";
  }
}
