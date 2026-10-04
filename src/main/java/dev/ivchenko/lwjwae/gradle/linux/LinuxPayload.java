package dev.ivchenko.lwjwae.gradle.linux;

import dev.ivchenko.lwjwae.gradle.util.Icons;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import lombok.experimental.UtilityClass;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

/**
 * What every Linux package installs, whatever its format: the executable in {@code /usr/bin}, the
 * desktop entry in {@code /usr/share/applications}, and the icon in the {@code hicolor} theme at
 * every common size, so the application shows up in the menu with its icon once installed. The
 * types of files that it opens go to {@code /usr/share/mime/packages}, which the triggers of {@code
 * shared-mime-info} and the hooks of pacman compile into the database of the desktop.
 */
@UtilityClass
class LinuxPayload {
  static final int EXECUTABLE = 0755;
  static final int REGULAR = 0644;

  /**
   * The files of the package.
   *
   * @param executable The native executable.
   * @param name The name of the executable and of the desktop entry.
   * @param desktop The desktop entry.
   * @param icon The icon, or {@code null} for none.
   * @param iconName The name of the icon in the theme.
   * @param sharedMimeInfo The types of files that the application opens, as a shared-mime-info
   *     package, or {@code null} for none.
   */
  List<PayloadFile> files(
      Path executable,
      String name,
      String desktop,
      File icon,
      String iconName,
      String sharedMimeInfo) {
    List<PayloadFile> files = new ArrayList<>();
    files.add(PayloadFile.of("usr/bin/" + name, EXECUTABLE, executable));
    files.add(
        PayloadFile.of(
            "usr/share/applications/" + name + ".desktop",
            REGULAR,
            desktop.getBytes(StandardCharsets.UTF_8)));
    if (sharedMimeInfo != null) {
      files.add(
          PayloadFile.of(
              "usr/share/mime/packages/" + name + ".xml",
              REGULAR,
              sharedMimeInfo.getBytes(StandardCharsets.UTF_8)));
    }
    if (icon != null) {
      BufferedImage image = Icons.read(icon);
      for (int size : Icons.LINUX_SIZES) {
        files.add(
            PayloadFile.of(
                "usr/share/icons/hicolor/" + size + "x" + size + "/apps/" + iconName + ".png",
                REGULAR,
                Icons.png(Icons.scale(image, size))));
      }
    }
    return files;
  }

  /** The directories that hold {@code files}, parents first: {@code usr/}, {@code usr/bin/}. */
  SortedSet<String> directories(List<PayloadFile> files) {
    SortedSet<String> directories = new TreeSet<>();
    for (PayloadFile file : files) {
      for (int slash = file.path().indexOf('/');
          slash >= 0;
          slash = file.path().indexOf('/', slash + 1)) {
        directories.add(file.path().substring(0, slash + 1));
      }
    }
    return directories;
  }

  /** The installed size of {@code files} in bytes. */
  long size(List<PayloadFile> files) throws IOException {
    long size = 0;
    for (PayloadFile file : files) {
      size += file.size();
    }
    return size;
  }

  /**
   * Writes the directories and the files into {@code tar}, owned by root.
   *
   * @param prefix What goes in front of every path: {@code ./} in a Debian package, nothing in an
   *     Arch one.
   * @param time The modification time of every entry.
   */
  void write(TarArchiveOutputStream tar, String prefix, List<PayloadFile> files, Instant time)
      throws IOException {
    for (String directory : LinuxPayload.directories(files)) {
      TarArchiveEntry entry = LinuxPayload.entry(prefix + directory, EXECUTABLE, time);
      tar.putArchiveEntry(entry);
      tar.closeArchiveEntry();
    }
    for (PayloadFile file : files) {
      LinuxPayload.write(tar, prefix + file.path(), file, time);
    }
  }

  /** Writes one file into {@code tar}, owned by root. */
  void write(TarArchiveOutputStream tar, String path, PayloadFile file, Instant time)
      throws IOException {
    TarArchiveEntry entry = LinuxPayload.entry(path, file.mode(), time);
    entry.setSize(file.size());
    tar.putArchiveEntry(entry);
    try (InputStream content = file.open()) {
      content.transferTo(tar);
    }
    tar.closeArchiveEntry();
  }

  private TarArchiveEntry entry(String path, int mode, Instant time) {
    TarArchiveEntry entry = new TarArchiveEntry(path);
    entry.setMode(mode);
    entry.setUserName("root");
    entry.setGroupName("root");
    entry.setModTime(time.toEpochMilli());
    return entry;
  }
}
