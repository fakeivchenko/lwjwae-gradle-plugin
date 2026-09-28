package dev.ivchenko.lwjwae.gradle.linux;

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
   */
  public String render(
      String name, String comment, String executable, String icon, List<String> categories) {
    return """
    [Desktop Entry]
    Type=Application
    Name=%s
    %sExec=%s
    Icon=%s
    Terminal=false
    Categories=%s;
    """
        .formatted(
            DesktopEntry.escape(name),
            comment == null || comment.isBlank() || comment.strip().equals(name.strip())
                ? ""
                : "Comment=" + DesktopEntry.escape(comment) + "\n",
            executable,
            icon,
            String.join(";", categories));
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
