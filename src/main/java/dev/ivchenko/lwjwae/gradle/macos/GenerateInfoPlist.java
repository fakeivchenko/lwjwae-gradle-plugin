package dev.ivchenko.lwjwae.gradle.macos;

import dev.ivchenko.lwjwae.gradle.util.Xml;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Writes the {@code Info.plist} file that is embedded into the {@code __info_plist} section of the
 * macOS executable.
 */
@CacheableTask
public abstract class GenerateInfoPlist extends DefaultTask {
  /** {@code CFBundleIdentifier}. */
  @Input
  public abstract Property<String> getBundleIdentifier();

  /** {@code CFBundleName} and {@code CFBundleDisplayName}. */
  @Input
  public abstract Property<String> getBundleName();

  /** {@code CFBundleShortVersionString}. */
  @Input
  public abstract Property<String> getVersion();

  /** The property list to write. */
  @OutputFile
  public abstract RegularFileProperty getInfoPlist();

  /** Writes the property list. */
  @TaskAction
  @SneakyThrows
  public void generate() {
    String plist =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "https://www.apple.com/DTDs/PropertyList-1.0.dtd">
        <plist version="1.0">
        <dict>
            <key>CFBundleIdentifier</key>
            <string>%s</string>
            <key>CFBundleName</key>
            <string>%s</string>
            <key>CFBundleDisplayName</key>
            <string>%s</string>
            <key>CFBundlePackageType</key>
            <string>APPL</string>
            <key>CFBundleShortVersionString</key>
            <string>%s</string>
            <key>NSHighResolutionCapable</key>
            <true/>
        </dict>
        </plist>
        """
            .formatted(
                GenerateInfoPlist.escape(this.getBundleIdentifier().get()),
                GenerateInfoPlist.escape(this.getBundleName().get()),
                GenerateInfoPlist.escape(this.getBundleName().get()),
                GenerateInfoPlist.escape(this.getVersion().get()));
    Path output = this.getInfoPlist().get().getAsFile().toPath();
    Files.createDirectories(output.getParent());
    Files.writeString(output, plist, StandardCharsets.UTF_8);
  }

  private static String escape(String text) {
    return Xml.escape(text);
  }
}
