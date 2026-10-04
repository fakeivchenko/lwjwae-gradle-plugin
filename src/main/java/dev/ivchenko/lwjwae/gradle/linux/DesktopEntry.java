package dev.ivchenko.lwjwae.gradle.linux;

import dev.ivchenko.lwjwae.gradle.FileAssociation;
import dev.ivchenko.lwjwae.gradle.util.Xml;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

/** Writes the {@code .desktop} file that a Linux desktop shows in its menu. */
@UtilityClass
public class DesktopEntry {
  /**
   * Returns the entry text.
   *
   * @param name What the menu shows.
   * @param comment One line about the application; left out when it only repeats {@code name},
   *     which linters of desktop entries warn about.
   * @param executable The {@code Exec} line: a path, or {@code AppRun} inside an AppImage.
   * @param icon The icon name, without extension.
   * @param categories The menu categories.
   * @param mimeTypes The media types that the application opens, {@code x-scheme-handler/SCHEME}
   *     for a scheme of links; with any, the desktop passes what it opens as arguments, {@code %U}.
   */
  public String render(
      String name,
      String comment,
      String executable,
      String icon,
      List<String> categories,
      List<String> mimeTypes) {
    return """
    [Desktop Entry]
    Type=Application
    Name=%s
    %sExec=%s%s
    Icon=%s
    Terminal=false
    Categories=%s;
    %s\
    """
        .formatted(
            DesktopEntry.escape(name),
            comment == null || comment.isBlank() || comment.strip().equals(name.strip())
                ? ""
                : "Comment=" + DesktopEntry.escape(comment) + "\n",
            executable,
            mimeTypes.isEmpty() ? "" : " %U",
            icon,
            String.join(";", categories),
            mimeTypes.isEmpty() ? "" : "MimeType=" + String.join(";", mimeTypes) + ";\n");
  }

  /**
   * The media types of a desktop entry for {@code schemes} and {@code fileTypes}: {@code
   * x-scheme-handler/SCHEME} for each scheme, then the media type of each type of file.
   */
  public List<String> mimeTypes(List<String> schemes, List<FileAssociation> fileTypes) {
    List<String> types = new ArrayList<>();
    schemes.forEach(scheme -> types.add("x-scheme-handler/" + scheme));
    fileTypes.forEach(type -> types.add(type.mimeType()));
    return types;
  }

  /**
   * The shared-mime-info package that tells the desktop which extension makes a file of each of
   * {@code fileTypes}, for {@code /usr/share/mime/packages}, or {@code null} for none. A type that
   * the desktop knows already, such as {@code text/markdown}, gets one more pattern, which does no
   * harm.
   */
  public String sharedMimeInfo(List<FileAssociation> fileTypes) {
    if (fileTypes.isEmpty()) {
      return null;
    }
    StringBuilder types = new StringBuilder();
    for (FileAssociation type : fileTypes) {
      types.append(
          """
            <mime-type type="%s">
              <comment>%s</comment>
              <glob pattern="*.%s"/>
            </mime-type>
          """
              .formatted(type.mimeType(), Xml.escape(type.description()), type.extension()));
    }
    return """
    <?xml version="1.0" encoding="UTF-8"?>
    <mime-info xmlns="http://www.freedesktop.org/standards/shared-mime-info">
    %s</mime-info>
    """
        .formatted(types);
  }

  /**
   * Escapes a string value by the Desktop Entry Specification: a line break or a tab in a name
   * would otherwise end the entry or break the key.
   */
  String escape(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\t", "\\t")
        .replace("\r", "\\r");
  }
}
