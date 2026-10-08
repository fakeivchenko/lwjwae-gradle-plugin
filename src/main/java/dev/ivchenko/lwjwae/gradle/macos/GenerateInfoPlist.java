package dev.ivchenko.lwjwae.gradle.macos;

import dev.ivchenko.lwjwae.gradle.Associations;
import dev.ivchenko.lwjwae.gradle.FileAssociation;
import dev.ivchenko.lwjwae.gradle.util.Xml;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
 *
 * <p>The schemes of links go to {@code CFBundleURLTypes}, and the types of files to {@code
 * CFBundleDocumentTypes} by their extensions and media types, which Launch Services matches without
 * a declaration of a uniform type identifier of their own.
 */
@CacheableTask
public abstract class GenerateInfoPlist extends DefaultTask implements Associations {
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
        %s</dict>
        </plist>
        """
            .formatted(
                GenerateInfoPlist.escape(this.getBundleIdentifier().get()),
                GenerateInfoPlist.escape(this.getBundleName().get()),
                GenerateInfoPlist.escape(this.getBundleName().get()),
                GenerateInfoPlist.escape(this.getVersion().get()),
                this.associations());
    Path output = this.getInfoPlist().get().getAsFile().toPath();
    Files.createDirectories(output.getParent());
    Files.writeString(output, plist, StandardCharsets.UTF_8);
  }

  /** {@code CFBundleURLTypes} and {@code CFBundleDocumentTypes}, or nothing without either. */
  private String associations() {
    StringBuilder keys = new StringBuilder();
    List<String> schemes = this.getUrlSchemes().get();
    if (!schemes.isEmpty()) {
      StringBuilder strings = new StringBuilder();
      schemes.forEach(
          scheme ->
              strings
                  .append("                <string>")
                  .append(GenerateInfoPlist.escape(scheme))
                  .append("</string>\n"));
      keys.append(
          """
              <key>CFBundleURLTypes</key>
              <array>
                  <dict>
                      <key>CFBundleURLName</key>
                      <string>%s</string>
                      <key>CFBundleURLSchemes</key>
                      <array>
          %s            </array>
                  </dict>
              </array>
          """
              .formatted(GenerateInfoPlist.escape(this.getBundleIdentifier().get()), strings));
    }
    List<FileAssociation> fileTypes = this.getFileTypes().get();
    if (!fileTypes.isEmpty()) {
      StringBuilder types = new StringBuilder();
      for (FileAssociation type : fileTypes) {
        types.append(
            """
                    <dict>
                        <key>CFBundleTypeName</key>
                        <string>%s</string>
                        <key>CFBundleTypeRole</key>
                        <string>Editor</string>
                        <key>CFBundleTypeExtensions</key>
                        <array>
                            <string>%s</string>
                        </array>
                        <key>CFBundleTypeMIMETypes</key>
                        <array>
                            <string>%s</string>
                        </array>
                    </dict>
            """
                .formatted(
                    GenerateInfoPlist.escape(type.description()),
                    type.extension(),
                    type.mimeType()));
      }
      keys.append(
          """
              <key>CFBundleDocumentTypes</key>
              <array>
          %s    </array>
          """
              .formatted(types));
    }
    return keys.toString();
  }

  private static String escape(String text) {
    return Xml.escape(text);
  }
}
