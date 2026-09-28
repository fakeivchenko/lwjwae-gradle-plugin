package dev.ivchenko.lwjwae.gradle.macos;

import dev.ivchenko.lwjwae.gradle.util.Icons;
import dev.ivchenko.lwjwae.gradle.util.Xml;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.work.DisableCachingByDefault;

/**
 * Lays out the macOS {@code .app} bundle: the executable in {@code Contents/MacOS}, the {@code
 * Info.plist}, the icon as {@code .icns} in {@code Contents/Resources}, and {@code PkgInfo}.
 *
 * <p>The bundle is what Finder, the Dock, and Launchpad know how to show and launch; the bare
 * executable works too, but without an icon and with a Terminal window. When a signing identity is
 * set, {@code codesign} signs the bundle; the task can lay it out on any platform, but signs only
 * on macOS.
 */
@DisableCachingByDefault(because = "Packs an executable that is already a build output")
public abstract class PackageMacApp extends DefaultTask {
  /** The native executable. */
  @InputFile
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getExecutable();

  /** The {@code Info.plist} to put in the bundle. */
  @InputFile
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getInfoPlist();

  /** The application icon, or nothing. */
  @InputFile
  @Optional
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getIcon();

  /** The name of the executable inside the bundle. */
  @Input
  public abstract Property<String> getImageName();

  /** The {@code codesign} identity, or nothing for an unsigned bundle. */
  @Input
  @Optional
  public abstract Property<String> getSigningIdentity();

  /** The {@code .app} directory to write. */
  @OutputDirectory
  public abstract DirectoryProperty getBundle();

  /** Gradle's process runner. */
  @Inject
  protected abstract ExecOperations getExecOperations();

  /** Gradle's file operations. */
  @Inject
  protected abstract FileSystemOperations getFileSystemOperations();

  /** Lays the bundle out. */
  @TaskAction
  @SneakyThrows
  public void build() {
    Path bundle = this.getBundle().get().getAsFile().toPath();
    this.getFileSystemOperations().delete(spec -> spec.delete(bundle));
    Path contents = Files.createDirectories(bundle.resolve("Contents"));
    Path macos = Files.createDirectories(contents.resolve("MacOS"));

    Path program = macos.resolve(this.getImageName().get());
    Files.copy(
        this.getExecutable().get().getAsFile().toPath(),
        program,
        StandardCopyOption.REPLACE_EXISTING);
    program.toFile().setExecutable(true, false);
    Files.writeString(contents.resolve("PkgInfo"), "APPL????", StandardCharsets.US_ASCII);

    String plist =
        Files.readString(this.getInfoPlist().get().getAsFile().toPath(), StandardCharsets.UTF_8);
    if (this.getIcon().isPresent()) {
      Path resources = Files.createDirectories(contents.resolve("Resources"));
      Files.write(
          resources.resolve("app.icns"), Icons.icns(Icons.read(this.getIcon().get().getAsFile())));
      plist = PackageMacApp.withIcon(plist, this.getImageName().get());
    } else {
      plist = PackageMacApp.withExecutable(plist, this.getImageName().get());
    }
    Files.writeString(contents.resolve("Info.plist"), plist, StandardCharsets.UTF_8);

    if (this.getSigningIdentity().isPresent()) {
      this.getExecOperations()
          .exec(
              spec -> {
                spec.setExecutable("codesign");
                spec.args(
                    "--force",
                    "--deep",
                    "--sign",
                    this.getSigningIdentity().get(),
                    bundle.toString());
              });
    }
  }

  /**
   * Adds the executable name and the icon to a property list that the plugin generated for the bare
   * executable, which has neither: a bundle needs {@code CFBundleExecutable}, and shows an icon
   * only through {@code CFBundleIconFile}.
   */
  static String withIcon(String plist, String executable) {
    return PackageMacApp.withKey(
        PackageMacApp.withExecutable(plist, executable), "CFBundleIconFile", "app.icns");
  }

  static String withExecutable(String plist, String executable) {
    return PackageMacApp.withKey(plist, "CFBundleExecutable", executable);
  }

  /**
   * Adds {@code key} with a string {@code value} as the first entry of the top dictionary, unless
   * the list has the key already.
   *
   * @throws GradleException If the list has no top dictionary to add to.
   */
  static String withKey(String plist, String key, String value) {
    if (Pattern.compile("<key>\\s*" + Pattern.quote(key) + "\\s*</key>").matcher(plist).find()) {
      return plist;
    }
    Matcher dictionary = Pattern.compile("<dict>").matcher(plist);
    if (!dictionary.find()) {
      throw new GradleException("The Info.plist has no <dict> to add " + key + " to");
    }
    return plist.substring(0, dictionary.end())
        + "\n    <key>"
        + key
        + "</key>\n    <string>"
        + Xml.escape(value)
        + "</string>"
        + plist.substring(dictionary.end());
  }
}
