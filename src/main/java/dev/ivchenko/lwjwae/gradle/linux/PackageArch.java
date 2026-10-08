package dev.ivchenko.lwjwae.gradle.linux;

import dev.ivchenko.lwjwae.gradle.Associations;
import dev.ivchenko.lwjwae.gradle.util.Platform;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;
import lombok.SneakyThrows;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.gradle.api.DefaultTask;
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
 * Builds an Arch Linux package around the native executable, in Java, without {@code makepkg}.
 *
 * <p>A package of pacman is a compressed tar archive: {@code .PKGINFO} with the name, the version,
 * and the dependencies; {@code .MTREE}, a gzipped list of every file with its permissions, size,
 * and SHA-256, which {@code pacman -Qkk} checks the installed files against; and the files
 * themselves, the same as those of the Debian package. The archive is compressed with xz, which
 * pacman reads as it reads the zstd of {@code makepkg}, and which needs no native code to write.
 */
@CacheableTask
public abstract class PackageArch extends DefaultTask implements Associations {
  /** The native executable. */
  @InputFile
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getExecutable();

  /** The application icon, or nothing. */
  @InputFile
  @Optional
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getIcon();

  /** The name of the icon in the icon theme, unique to the application. */
  @Input
  public abstract Property<String> getIconName();

  /** The package name. */
  @Input
  public abstract Property<String> getPackageName();

  /** The name shown in the menu. */
  @Input
  public abstract Property<String> getDisplayName();

  /** The {@code pkgver}. */
  @Input
  public abstract Property<String> getVersion();

  /** The {@code pkgrel}. */
  @Input
  public abstract Property<String> getRelease();

  /** The architecture, in Arch terms: {@code x86_64} or {@code aarch64}. */
  @Input
  public abstract Property<String> getArchitecture();

  /** The packager. */
  @Input
  public abstract Property<String> getPackager();

  /** The one-line description. */
  @Input
  public abstract Property<String> getSummary();

  /** The homepage, if any. */
  @Input
  @Optional
  public abstract Property<String> getHomepage();

  /** The dependencies. */
  @Input
  public abstract ListProperty<String> getDepends();

  /** The licenses. */
  @Input
  public abstract ListProperty<String> getLicenses();

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
    String name = this.getPackageName().get();
    String desktop =
        DesktopEntry.render(
            this.getDisplayName().get(),
            this.getSummary().get(),
            "/usr/bin/" + name,
            this.getIconName().get(),
            this.getCategories().get(),
            DesktopEntry.mimeTypes(this.getUrlSchemes().get(), this.getFileTypes().get()));
    List<PayloadFile> files =
        LinuxPayload.files(
            this.getExecutable().get().getAsFile().toPath(),
            name,
            desktop,
            this.getIcon().isPresent() ? this.getIcon().get().getAsFile() : null,
            this.getIconName().get(),
            DesktopEntry.sharedMimeInfo(this.getFileTypes().get()));
    // Whole seconds: .PKGINFO and .MTREE count in them, and the tar entries have to agree.
    Instant time = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    PayloadFile packageInfo =
        PayloadFile.of(
            ".PKGINFO",
            LinuxPayload.REGULAR,
            this.packageInfo(LinuxPayload.size(files), time).getBytes(StandardCharsets.UTF_8));
    PayloadFile mtree =
        PayloadFile.of(".MTREE", LinuxPayload.REGULAR, PackageArch.mtree(packageInfo, files, time));

    File output = this.getPackageFile().get().getAsFile();
    Files.createDirectories(output.getParentFile().toPath());
    try (TarArchiveOutputStream tar =
        new TarArchiveOutputStream(
            new XZCompressorOutputStream(Files.newOutputStream(output.toPath())))) {
      tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
      // pacman reads the metadata from the front of the archive.
      LinuxPayload.write(tar, packageInfo.path(), packageInfo, time);
      LinuxPayload.write(tar, mtree.path(), mtree, time);
      LinuxPayload.write(tar, "", files, time);
    }
  }

  /** Returns {@code .PKGINFO}, in the order of the fields that {@code makepkg} writes. */
  String packageInfo(long installedBytes, Instant time) {
    StringBuilder info = new StringBuilder("# Generated by the lwjwae Gradle plugin\n");
    PackageArch.field(info, "pkgname", this.getPackageName().get());
    PackageArch.field(info, "pkgbase", this.getPackageName().get());
    PackageArch.field(info, "xdata", "pkgtype=pkg");
    PackageArch.field(info, "pkgver", this.getVersion().get() + "-" + this.getRelease().get());
    PackageArch.field(info, "pkgdesc", this.getSummary().get());
    if (this.getHomepage().isPresent()) {
      PackageArch.field(info, "url", this.getHomepage().get());
    }
    PackageArch.field(info, "builddate", String.valueOf(time.getEpochSecond()));
    PackageArch.field(info, "packager", this.getPackager().get());
    PackageArch.field(info, "size", String.valueOf(installedBytes));
    PackageArch.field(info, "arch", this.getArchitecture().get());
    for (String license : this.getLicenses().get()) {
      PackageArch.field(info, "license", license);
    }
    for (String depend : this.getDepends().get()) {
      PackageArch.field(info, "depend", depend);
    }
    return info.toString();
  }

  /** The Arch name of the architecture that runs the build. */
  public static String architecture() {
    return Platform.isArm64() ? "aarch64" : "x86_64";
  }

  /**
   * Returns an Arch package name made from {@code name}: lowercase, with every character that the
   * format doesn't allow replaced by a hyphen, and none in front.
   */
  public static String packageName(String name) {
    return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9@._+-]", "-").replaceAll("^[-.]+", "");
  }

  /** Returns a {@code pkgver} made from {@code version}: hyphens and the like as underscores. */
  public static String packageVersion(String version) {
    return version.replaceAll("[-:/\\s]", "_");
  }

  /**
   * The gzipped {@code .MTREE} of {@code packageInfo} and {@code files}, as {@code bsdtar} writes
   * it for {@code makepkg}: the defaults on a {@code /set} line, and on each line what differs from
   * them.
   */
  static byte[] mtree(PayloadFile packageInfo, List<PayloadFile> files, Instant time)
      throws IOException {
    String stamp = " time=" + time.getEpochSecond() + ".0";
    StringBuilder mtree = new StringBuilder("#mtree\n/set type=file uid=0 gid=0 mode=644\n");
    PackageArch.mtreeFile(mtree, packageInfo, stamp);
    for (String directory : LinuxPayload.directories(files)) {
      String path = directory.substring(0, directory.length() - 1);
      mtree
          .append("./")
          .append(PackageArch.escape(path))
          .append(stamp)
          .append(" mode=755 type=dir\n");
    }
    for (PayloadFile file : files) {
      PackageArch.mtreeFile(mtree, file, stamp);
    }
    ByteArrayOutputStream gzip = new ByteArrayOutputStream();
    try (OutputStream out = new GZIPOutputStream(gzip)) {
      out.write(mtree.toString().getBytes(StandardCharsets.UTF_8));
    }
    return gzip.toByteArray();
  }

  private static void mtreeFile(StringBuilder mtree, PayloadFile file, String stamp)
      throws IOException {
    mtree.append("./").append(PackageArch.escape(file.path())).append(stamp);
    if (file.mode() != LinuxPayload.REGULAR) {
      mtree.append(" mode=").append(Integer.toOctalString(file.mode()));
    }
    mtree.append(" size=").append(file.size());
    mtree.append(" sha256digest=").append(PackageArch.sha256(file)).append('\n');
  }

  /** A path of an mtree: every character but the plain ones as a backslash and three octals. */
  static String escape(String path) {
    StringBuilder escaped = new StringBuilder();
    for (byte character : path.getBytes(StandardCharsets.UTF_8)) {
      if ((character >= 'a' && character <= 'z')
          || (character >= 'A' && character <= 'Z')
          || (character >= '0' && character <= '9')
          || "._/@+-,:=%".indexOf(character) >= 0) {
        escaped.append((char) character);
      } else {
        escaped.append('\\').append(String.format("%03o", character & 0xFF));
      }
    }
    return escaped.toString();
  }

  private static String sha256(PayloadFile file) throws IOException {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    try (InputStream in = new DigestInputStream(file.open(), digest)) {
      in.transferTo(OutputStream.nullOutputStream());
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static void field(StringBuilder info, String key, String value) {
    // A line break would start a field of its own.
    info.append(key).append(" = ").append(value.replaceAll("\\s+", " ").strip()).append('\n');
  }
}
