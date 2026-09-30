package dev.ivchenko.lwjwae.gradle.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/**
 * Writes {@code META-INF/lwjwae/update.properties}, which {@code UpdateParameters.discover()} of
 * the library reads: the manifest URL, the public key, and the version of the application.
 */
@CacheableTask
public abstract class GenerateUpdateProperties extends DefaultTask {
  /** The path of the file in the resources, as the library looks it up. */
  public static final String RESOURCE = "META-INF/lwjwae/update.properties";

  /** The URL of the manifest. */
  @Input
  public abstract Property<String> getManifestUrl();

  /** The public key, as Base64 of its X.509 encoding. */
  @Input
  public abstract Property<String> getPublicKey();

  /** The version of the application. */
  @Input
  public abstract Property<String> getVersion();

  /** The root of the resources that the file goes into. */
  @OutputDirectory
  public abstract DirectoryProperty getOutputDirectory();

  /** Writes the file. */
  @TaskAction
  public void generate() throws IOException {
    Path file = this.getOutputDirectory().get().getAsFile().toPath().resolve(RESOURCE);
    Files.createDirectories(file.getParent());
    Files.writeString(
        file,
        "manifestUrl="
            + GenerateUpdateProperties.escape(this.getManifestUrl().get())
            + "\npublicKey="
            + GenerateUpdateProperties.escape(this.getPublicKey().get())
            + "\nversion="
            + GenerateUpdateProperties.escape(this.getVersion().get())
            + "\n",
        StandardCharsets.ISO_8859_1);
  }

  /** {@code value} as a value of a properties file, which reads a backslash as an escape. */
  static String escape(String value) {
    StringBuilder escaped = new StringBuilder();
    for (char character : value.strip().toCharArray()) {
      if (character == '\\') {
        escaped.append("\\\\");
      } else if (character > 0x7E || character < 0x20) {
        escaped.append("\\u%04x".formatted((int) character));
      } else {
        escaped.append(character);
      }
    }
    return escaped.toString();
  }
}
