package dev.ivchenko.lwjwae.gradle.windows;

import dev.ivchenko.lwjwae.gradle.util.Executables;
import dev.ivchenko.lwjwae.gradle.util.Xml;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import javax.inject.Inject;
import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.work.DisableCachingByDefault;

/**
 * Builds a Windows installer with the WiX Toolset.
 *
 * <p>The task writes one {@code .wxs} source: the executable in the install folder, a Start menu
 * shortcut, the icon for Settings, and a {@code MajorUpgrade} so that a newer installer replaces
 * the older installation. Per-user installs go under {@code %LOCALAPPDATA%\Programs} without
 * elevation, and keep their key path in {@code HKCU}, as Windows Installer requires for files in a
 * user profile. {@code wix build} turns the source into the {@code .msi}. Windows only.
 */
@DisableCachingByDefault(because = "Runs the WiX toolset over a build output")
public abstract class PackageMsi extends DefaultTask {
  /** The WiX release that the plugin installs when none is on the {@code PATH}. */
  public static final String WIX_VERSION = "6.0.2";

  /** The native executable. */
  @InputFile
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getExecutable();

  /** The {@code .ico} file, or nothing. */
  @InputFile
  @Optional
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getIcon();

  /** The product name shown in Settings and the Start menu. */
  @Input
  public abstract Property<String> getProductName();

  /** The manufacturer shown in Settings. */
  @Input
  public abstract Property<String> getManufacturer();

  /** The version, as {@code major.minor.patch}. */
  @Input
  public abstract Property<String> getVersion();

  /** The upgrade code GUID. */
  @Input
  public abstract Property<String> getUpgradeCode();

  /** Whether the install is per user. */
  @Input
  public abstract Property<Boolean> getPerUser();

  /** The name of the executable file. */
  @Input
  public abstract Property<String> getExecutableName();

  /** The WiX version to install when none is found. */
  @Input
  public abstract Property<String> getWixVersion();

  /** A {@code wix} tool to use instead of the one from the {@code PATH} or the cache. */
  @InputFile
  @Optional
  @PathSensitive(PathSensitivity.ABSOLUTE)
  public abstract RegularFileProperty getTool();

  /** Where the installed tool lives between builds. */
  @Internal
  public abstract DirectoryProperty getCacheDirectory();

  /** The directory for the generated source. */
  @Internal
  public abstract DirectoryProperty getWorkDirectory();

  /** The installer to write. */
  @OutputFile
  public abstract RegularFileProperty getInstaller();

  /** Gradle's process runner. */
  @Inject
  protected abstract ExecOperations getExecOperations();

  /** Writes the source and builds the installer. */
  @TaskAction
  @SneakyThrows
  public void build() {
    Path work = Files.createDirectories(this.getWorkDirectory().get().getAsFile().toPath());
    Path source = work.resolve("app.wxs");
    Files.writeString(source, this.source(), StandardCharsets.UTF_8);

    File output = this.getInstaller().get().getAsFile();
    Files.createDirectories(output.getParentFile().toPath());
    String tool =
        this.getTool().isPresent()
            ? this.getTool().get().getAsFile().getAbsolutePath()
            : this.tool();
    this.getExecOperations()
        .exec(
            spec -> {
              spec.setExecutable(tool);
              // No .wixpdb: it is debug data for WiX, and would land next to the installer.
              spec.args(
                  "build",
                  "-arch",
                  "x64",
                  "-pdbtype",
                  "none",
                  "-o",
                  output.getAbsolutePath(),
                  source.toString());
              spec.setWorkingDir(work.toFile());
            });
  }

  /** Returns the WiX source of the installer. */
  String source() {
    boolean perUser = this.getPerUser().get();
    String executable = this.getExecutableName().get();
    String product = PackageMsi.escape(this.getProductName().get());
    String icon =
        this.getIcon().isPresent()
            ? """
                <Icon Id="AppIcon" SourceFile="%s" />
                <Property Id="ARPPRODUCTICON" Value="AppIcon" />
            """
                .formatted(PackageMsi.escape(this.getIcon().get().getAsFile().getAbsolutePath()))
            : "";
    String iconAttribute = this.getIcon().isPresent() ? " Icon=\"AppIcon\"" : "";
    String folder =
        perUser
            ? """
                <StandardDirectory Id="LocalAppDataFolder">
                  <Directory Id="ProgramsFolder" Name="Programs">
                    <Directory Id="INSTALLFOLDER" Name="%s" />
                  </Directory>
                </StandardDirectory>
            """
                .formatted(product)
            : """
                <StandardDirectory Id="ProgramFiles64Folder">
                  <Directory Id="INSTALLFOLDER" Name="%s" />
                </StandardDirectory>
            """
                .formatted(product);
    String keyPath =
        perUser
            ? """
                    <RegistryValue Root="HKCU" Key="Software\\%s\\%s" Name="Installed" Type="integer" Value="1" KeyPath="yes" />
                    <File Source="%s" />
            """
                .formatted(
                    PackageMsi.escape(this.getManufacturer().get()),
                    product,
                    PackageMsi.escape(this.getExecutable().get().getAsFile().getAbsolutePath()))
            : """
                    <File Source="%s" KeyPath="yes" />
            """
                .formatted(
                    PackageMsi.escape(this.getExecutable().get().getAsFile().getAbsolutePath()));
    return """
    <?xml version="1.0" encoding="UTF-8"?>
    <Wix xmlns="http://wixtoolset.org/schemas/v4/wxs">
      <Package Name="%s" Manufacturer="%s" Version="%s" UpgradeCode="%s" Scope="%s" Language="1033" Compressed="yes">
        <MajorUpgrade DowngradeErrorMessage="A newer version of [ProductName] is already installed." />
        <MediaTemplate EmbedCab="yes" />
    %s
    %s
        <StandardDirectory Id="ProgramMenuFolder" />

        <ComponentGroup Id="Application" Directory="INSTALLFOLDER">
          <Component Id="Executable" Guid="%s">
    %s
            <Shortcut Id="StartMenuShortcut" Directory="ProgramMenuFolder" Name="%s" WorkingDirectory="INSTALLFOLDER" Target="[INSTALLFOLDER]%s"%s />
            <RemoveFolder Id="RemoveInstallFolder" On="uninstall" />
          </Component>
        </ComponentGroup>

        <Feature Id="Main" Title="%s" Level="1">
          <ComponentGroupRef Id="Application" />
        </Feature>
      </Package>
    </Wix>
    """
        .formatted(
            product,
            PackageMsi.escape(this.getManufacturer().get()),
            PackageMsi.numericVersion(this.getVersion().get()),
            this.getUpgradeCode().get(),
            perUser ? "perUser" : "perMachine",
            icon,
            folder,
            PackageMsi.componentGuid(this.getUpgradeCode().get()),
            keyPath,
            product,
            executable,
            iconAttribute,
            product);
  }

  /**
   * Returns the {@code wix} executable: a {@code wix} on the {@code PATH}, or the one the task
   * installs into the cache with {@code dotnet tool install}.
   */
  private String tool() {
    return Executables.onPath("wix.exe").map(File::getAbsolutePath).orElseGet(this::installedTool);
  }

  /** Returns the {@code wix} in the cache, installing it with {@code dotnet} on first use. */
  private String installedTool() {
    String version = this.getWixVersion().get();
    File cache = this.getCacheDirectory().get().dir("wix-" + version).getAsFile();
    File wix = new File(cache, "wix.exe");
    if (!wix.isFile()) {
      this.getLogger().lifecycle("Installing WiX {} into {}", version, cache);
      this.getExecOperations()
          .exec(
              spec -> {
                spec.setExecutable("dotnet");
                spec.args(
                    "tool",
                    "install",
                    "wix",
                    "--version",
                    version,
                    "--tool-path",
                    cache.getAbsolutePath());
              });
      if (!wix.isFile()) {
        throw new GradleException("dotnet tool install produced no wix.exe in " + cache);
      }
    }
    return wix.getAbsolutePath();
  }

  /** Returns a stable GUID for a project: the same group and name give the same code. */
  public static String upgradeCode(String group, String name) {
    return UUID.nameUUIDFromBytes(("lwjwae:" + group + ":" + name).getBytes(StandardCharsets.UTF_8))
        .toString()
        .toUpperCase(Locale.ROOT);
  }

  /**
   * Returns the GUID of the one component, derived from the upgrade code. A component that holds a
   * file and a registry key path can't have an automatic GUID, and the GUID must stay the same
   * across versions for upgrades to replace the component rather than add a second copy.
   */
  static String componentGuid(String upgradeCode) {
    return UUID.nameUUIDFromBytes((upgradeCode + ":executable").getBytes(StandardCharsets.UTF_8))
        .toString()
        .toUpperCase(Locale.ROOT);
  }

  /**
   * Returns {@code 1.2.3} from {@code 1.2.3-SNAPSHOT}: the three parts that Windows Installer
   * compares, since it ignores a fourth when it decides on an upgrade.
   *
   * @throws GradleException If a part is beyond what an installer takes: 255 for the major and the
   *     minor version, 65535 for the build.
   */
  public static String numericVersion(String version) {
    String[] parts = GenerateWindowsResourceScript.numericVersion(version).split(",");
    int major = Integer.parseInt(parts[0]);
    int minor = Integer.parseInt(parts[1]);
    int build = Integer.parseInt(parts[2]);
    if (major > 255 || minor > 255 || build > 65535) {
      throw new GradleException(
          "An installer version is at most 255.255.65535, not "
              + version
              + "; give the project a version of that form");
    }
    return major + "." + minor + "." + build;
  }

  private static String escape(String text) {
    return Xml.escape(text);
  }
}
