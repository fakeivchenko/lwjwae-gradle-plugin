package dev.ivchenko.lwjwae.gradle.windows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Writes the {@code .rc} script, with the icon and the version block, that {@link
 * CompileWindowsResources} compiles.
 */
@DisableCachingByDefault(because = "The script embeds the absolute path of the icon")
public abstract class GenerateWindowsResourceScript extends DefaultTask {
  private static final Pattern VERSION_NUMBER =
      Pattern.compile("(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:\\.(\\d+))?");

  /** The {@code .ico} file, or nothing for an executable without an icon. */
  @InputFile
  @Optional
  @PathSensitive(PathSensitivity.ABSOLUTE)
  public abstract RegularFileProperty getIcon();

  /** {@code FileDescription}. */
  @Input
  public abstract Property<String> getFileDescription();

  /** {@code ProductName}. */
  @Input
  public abstract Property<String> getProductName();

  /** {@code CompanyName}, if any. */
  @Input
  @Optional
  public abstract Property<String> getCompanyName();

  /** {@code LegalCopyright}, if any. */
  @Input
  @Optional
  public abstract Property<String> getCopyright();

  /** {@code FileVersion} and {@code ProductVersion}, as text. */
  @Input
  public abstract Property<String> getVersion();

  /** {@code OriginalFilename}: the name of the executable. */
  @Input
  public abstract Property<String> getOriginalFilename();

  /** The script to write. */
  @OutputFile
  public abstract RegularFileProperty getScript();

  /** Writes the script. */
  @TaskAction
  @SneakyThrows
  public void generate() {
    String icon =
        this.getIcon().isPresent()
            ? "1 ICON \""
                + this.getIcon().get().getAsFile().getAbsolutePath().replace('\\', '/')
                + "\"\n\n"
            : "";
    String version = this.getVersion().get();
    String numeric = GenerateWindowsResourceScript.numericVersion(version);
    String optional =
        (this.getCompanyName().isPresent()
                ? GenerateWindowsResourceScript.value("CompanyName", this.getCompanyName().get())
                : "")
            + (this.getCopyright().isPresent()
                ? GenerateWindowsResourceScript.value("LegalCopyright", this.getCopyright().get())
                : "");
    // UTF-8, which rc.exe reads when the script says so: names and paths in any language.
    String script =
        "#pragma code_page(65001)\n\n"
            + icon
            + """
            1 VERSIONINFO
            FILEVERSION     %s
            PRODUCTVERSION  %s
            BEGIN
                BLOCK "StringFileInfo"
                BEGIN
                    BLOCK "040904b0"
                    BEGIN
            %s%s%s%s%s%s        END
                END
                BLOCK "VarFileInfo"
                BEGIN
                    VALUE "Translation", 0x409, 1200
                END
            END
            """
                .formatted(
                    numeric,
                    numeric,
                    GenerateWindowsResourceScript.value(
                        "FileDescription", this.getFileDescription().get()),
                    GenerateWindowsResourceScript.value("ProductName", this.getProductName().get()),
                    GenerateWindowsResourceScript.value("FileVersion", version),
                    GenerateWindowsResourceScript.value("ProductVersion", version),
                    GenerateWindowsResourceScript.value(
                        "OriginalFilename", this.getOriginalFilename().get()),
                    optional);

    Path output = this.getScript().get().getAsFile().toPath();
    Files.createDirectories(output.getParent());
    // Windows line endings, as rc.exe expects them.
    Files.writeString(output, script.replace("\n", "\r\n"), StandardCharsets.UTF_8);
  }

  /**
   * Returns the numeric form that the version block requires: {@code 1.2.3-SNAPSHOT} becomes {@code
   * 1,2,3,0}, and text without a leading number becomes {@code 0,0,0,0}.
   */
  public static String numericVersion(String version) {
    Matcher matcher = VERSION_NUMBER.matcher(version);
    if (!matcher.lookingAt()) {
      return "0,0,0,0";
    }
    StringBuilder numeric = new StringBuilder();
    for (int group = 1; group <= 4; group++) {
      if (group > 1) {
        numeric.append(',');
      }
      numeric.append(matcher.group(group) == null ? "0" : matcher.group(group));
    }
    return numeric.toString();
  }

  /** One line of the string table, with the quotes of {@code text} doubled, as rc.exe wants. */
  private static String value(String name, String text) {
    return "            VALUE \"" + name + "\", \"" + text.replace("\"", "\"\"") + "\"\n";
  }
}
