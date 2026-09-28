package dev.ivchenko.lwjwae.gradle.linux;

import dev.ivchenko.lwjwae.gradle.util.Icons;
import dev.ivchenko.lwjwae.gradle.util.Platform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;
import lombok.SneakyThrows;
import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Builds a Debian package around the native executable, in Java, without {@code dpkg-deb}.
 *
 * <p>A {@code .deb} is an {@code ar} archive of three members: {@code debian-binary}, {@code
 * control.tar.gz} with the {@code control} file, and {@code data.tar.gz} with the files to install.
 * The executable goes to {@code /usr/bin}, the desktop entry to {@code /usr/share/applications},
 * and the icon to the {@code hicolor} theme at every common size, so the application shows up in
 * the menu with its icon right after {@code dpkg -i}.
 */
@CacheableTask
public abstract class PackageDeb extends DefaultTask {
  private static final int EXECUTABLE = 0755;
  private static final int REGULAR = 0644;

  /** The native executable. */
  @InputFile
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getExecutable();

  /** The application icon, or nothing. */
  @InputFile
  @Optional
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getIcon();

  /** The package name. */
  @Input
  public abstract Property<String> getPackageName();

  /** The name shown in the menu. */
  @Input
  public abstract Property<String> getDisplayName();

  /** The version. */
  @Input
  public abstract Property<String> getVersion();

  /** The architecture, in Debian terms: {@code amd64} or {@code arm64}. */
  @Input
  public abstract Property<String> getArchitecture();

  /** The maintainer, as {@code Name <email>}, which the package can't be built without. */
  @Input
  @Optional
  public abstract Property<String> getMaintainer();

  /** The one-line description. */
  @Input
  public abstract Property<String> getSummary();

  /** The homepage, if any. */
  @Input
  @Optional
  public abstract Property<String> getHomepage();

  /** The section. */
  @Input
  public abstract Property<String> getSection();

  /** The priority. */
  @Input
  public abstract Property<String> getPriority();

  /** The dependencies. */
  @Input
  public abstract ListProperty<String> getDepends();

  /** The desktop entry categories. */
  @Input
  public abstract ListProperty<String> getCategories();

  /** The package to write. */
  @OutputFile
  public abstract RegularFileProperty getPackageFile();

  /** Builds the package. */
  @TaskAction
  @SneakyThrows
  public void build() {
    if (!this.getMaintainer().isPresent()) {
      throw new GradleException(
          "A Debian package names its maintainer: set packaging.deb.maintainer, or"
              + " packaging.vendor, to \"Name <email>\"");
    }
    String name = this.getPackageName().get();
    File executable = this.getExecutable().get().getAsFile();
    long installedBytes = Files.size(executable.toPath());
    byte[] desktop =
        DesktopEntry.render(
                this.getDisplayName().get(),
                this.getSummary().get(),
                "/usr/bin/" + name,
                name,
                this.getCategories().get())
            .getBytes(StandardCharsets.UTF_8);

    File output = this.getPackageFile().get().getAsFile();
    Files.createDirectories(output.getParentFile().toPath());
    // The executable runs to tens of megabytes: it streams through a file, not the heap.
    Path data = Files.createTempFile(this.getTemporaryDir().toPath(), "data", ".tar.gz");
    try (TarArchiveOutputStream tar = PackageDeb.tar(Files.newOutputStream(data))) {
      PackageDeb.directory(tar, "./usr/");
      PackageDeb.directory(tar, "./usr/bin/");
      PackageDeb.file(tar, "./usr/bin/" + name, executable.toPath(), EXECUTABLE);
      PackageDeb.directory(tar, "./usr/share/");
      PackageDeb.directory(tar, "./usr/share/applications/");
      PackageDeb.file(tar, "./usr/share/applications/" + name + ".desktop", desktop, REGULAR);
      installedBytes += desktop.length;
      if (this.getIcon().isPresent()) {
        BufferedImage image = Icons.read(this.getIcon().get().getAsFile());
        PackageDeb.directory(tar, "./usr/share/icons/");
        PackageDeb.directory(tar, "./usr/share/icons/hicolor/");
        for (int size : Icons.LINUX_SIZES) {
          String sized = "./usr/share/icons/hicolor/" + size + "x" + size + "/";
          byte[] png = Icons.png(Icons.scale(image, size));
          PackageDeb.directory(tar, sized);
          PackageDeb.directory(tar, sized + "apps/");
          PackageDeb.file(tar, sized + "apps/" + name + ".png", png, REGULAR);
          installedBytes += png.length;
        }
      }
    }

    ByteArrayOutputStream control = new ByteArrayOutputStream();
    try (TarArchiveOutputStream tar = PackageDeb.tar(control)) {
      PackageDeb.file(
          tar,
          "./control",
          this.control((installedBytes + 1023) / 1024).getBytes(StandardCharsets.UTF_8),
          REGULAR);
    }

    try (ArArchiveOutputStream ar =
        new ArArchiveOutputStream(Files.newOutputStream(output.toPath()))) {
      PackageDeb.member(ar, "debian-binary", "2.0\n".getBytes(StandardCharsets.US_ASCII));
      PackageDeb.member(ar, "control.tar.gz", control.toByteArray());
      ar.putArchiveEntry(new ArArchiveEntry("data.tar.gz", Files.size(data)));
      Files.copy(data, ar);
      ar.closeArchiveEntry();
    } finally {
      Files.deleteIfExists(data);
    }
  }

  /** Returns the {@code control} file, in the field order that {@code dpkg-deb} writes. */
  String control(long installedKilobytes) {
    List<String> depends = this.getDepends().get();
    return """
    Package: %s
    Version: %s
    Architecture: %s
    Maintainer: %s
    Installed-Size: %d
    %sSection: %s
    Priority: %s
    %sDescription: %s
    """
        .formatted(
            this.getPackageName().get(),
            this.getVersion().get(),
            this.getArchitecture().get(),
            this.getMaintainer().get(),
            installedKilobytes,
            depends.isEmpty() ? "" : "Depends: " + String.join(", ", depends) + "\n",
            this.getSection().get(),
            this.getPriority().get(),
            this.getHomepage().map(homepage -> "Homepage: " + homepage + "\n").getOrElse(""),
            this.getSummary().get());
  }

  /** The Debian name of the architecture that runs the build. */
  public static String architecture() {
    return Platform.isArm64() ? "arm64" : "amd64";
  }

  /**
   * Returns a Debian package name made from {@code name}: lowercase, with every character that the
   * format doesn't allow replaced by a dash.
   */
  public static String packageName(String name) {
    return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+.-]", "-");
  }

  private static TarArchiveOutputStream tar(OutputStream out) throws IOException {
    TarArchiveOutputStream tar = new TarArchiveOutputStream(new GZIPOutputStream(out));
    tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
    return tar;
  }

  private static void directory(TarArchiveOutputStream tar, String path) throws IOException {
    TarArchiveEntry entry = new TarArchiveEntry(path);
    entry.setMode(EXECUTABLE);
    entry.setUserName("root");
    entry.setGroupName("root");
    tar.putArchiveEntry(entry);
    tar.closeArchiveEntry();
  }

  private static void file(TarArchiveOutputStream tar, String path, byte[] content, int mode)
      throws IOException {
    TarArchiveEntry entry = new TarArchiveEntry(path);
    entry.setSize(content.length);
    entry.setMode(mode);
    entry.setUserName("root");
    entry.setGroupName("root");
    tar.putArchiveEntry(entry);
    tar.write(content);
    tar.closeArchiveEntry();
  }

  private static void file(TarArchiveOutputStream tar, String path, Path content, int mode)
      throws IOException {
    TarArchiveEntry entry = new TarArchiveEntry(path);
    entry.setSize(Files.size(content));
    entry.setMode(mode);
    entry.setUserName("root");
    entry.setGroupName("root");
    tar.putArchiveEntry(entry);
    Files.copy(content, tar);
    tar.closeArchiveEntry();
  }

  private static void member(ArArchiveOutputStream ar, String name, byte[] content)
      throws IOException {
    ar.putArchiveEntry(new ArArchiveEntry(name, content.length));
    ar.write(content);
    ar.closeArchiveEntry();
  }
}
