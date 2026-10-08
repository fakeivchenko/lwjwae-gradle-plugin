package dev.ivchenko.lwjwae.gradle.windows;

import dev.ivchenko.lwjwae.gradle.Associations;
import dev.ivchenko.lwjwae.gradle.LwjwaeExtension;
import dev.ivchenko.lwjwae.gradle.LwjwaeLayout;
import dev.ivchenko.lwjwae.gradle.util.Platform;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

/**
 * Everything the plugin does for Windows: the defaults of {@code windows {}} and {@code
 * packaging.msi {}}, the tasks that turn the icon and the version strings into a {@code .res} file
 * for the linker, {@code signWindowsExecutable}, and {@code packageMsi}, which packs the signed
 * executable when a certificate is set and signs itself.
 *
 * <p>The resource tasks exist on every platform, so a build script can render the icon and write
 * the script anywhere; only {@code rc.exe} and the installer need Windows.
 */
@UtilityClass
public class WindowsConfiguration {
  private final List<Integer> ICON_SIZES = List.of(16, 24, 32, 48, 64, 128, 256);

  /**
   * Applies the defaults and registers the tasks.
   *
   * @return The task whose {@code .res} file goes to the linker of the native image.
   */
  public TaskProvider<CompileWindowsResources> configure(
      Project project, LwjwaeExtension extension, LwjwaeLayout layout) {
    WindowsConfiguration.applyDefaults(project, extension, layout);
    TaskProvider<CompileWindowsResources> resources =
        WindowsConfiguration.registerResourceTasks(project, extension, layout);
    Provider<RegularFile> executable =
        WindowsConfiguration.registerSigning(project, extension, layout);
    WindowsConfiguration.registerPackageMsi(project, extension, layout, executable);
    return resources;
  }

  private void applyDefaults(Project project, LwjwaeExtension extension, LwjwaeLayout layout) {
    // The vendor without the e-mail address that a Debian maintainer carries.
    Provider<String> company =
        extension.getVendor().map(vendor -> vendor.replaceAll("\\s*<[^>]*>\\s*$", ""));
    WindowsExtension windows = extension.getWindows();
    windows.getConsole().convention(false);
    windows.getIconSizes().convention(ICON_SIZES);
    windows.getFileDescription().convention(extension.getDisplayName());
    windows.getProductName().convention(extension.getDisplayName());
    windows.getCompanyName().convention(company);
    windows.getVersion().convention(layout.projectVersion());
    WindowsSigningExtension signing = windows.getSigning();
    signing
        .getCertificatePassword()
        .convention(
            project
                .getProviders()
                .gradleProperty("lwjwae.windowsCertificatePassword")
                .orElse(
                    project
                        .getProviders()
                        .environmentVariable("LWJWAE_WINDOWS_CERTIFICATE_PASSWORD")));
    signing.getTimestampUrl().convention(WindowsSigningExtension.DEFAULT_TIMESTAMP_URL);

    MsiExtension msi = extension.getPackaging().getMsi();
    msi.getEnabled().convention(false);
    msi.getPerUser().convention(true);
    msi.getUpgradeCode()
        .convention(
            project.provider(
                () -> PackageMsi.upgradeCode(project.getGroup().toString(), project.getName())));
    msi.getManufacturer().convention(company.orElse(extension.getDisplayName()));
    msi.getProductName().convention(extension.getDisplayName());
    msi.getWixVersion().convention(PackageMsi.WIX_VERSION);
    msi.getFileName().convention(extension.getImageName());
  }

  private TaskProvider<CompileWindowsResources> registerResourceTasks(
      Project project, LwjwaeExtension extension, LwjwaeLayout layout) {
    WindowsExtension windows = extension.getWindows();
    TaskProvider<GenerateWindowsIcon> icon =
        project
            .getTasks()
            .register(
                "generateWindowsIcon",
                GenerateWindowsIcon.class,
                task -> {
                  task.setDescription(
                      "Renders the application icon into a multi-size Windows .ico.");
                  task.setGroup("build");
                  task.getSource().set(extension.getIcon());
                  task.getSizes().set(windows.getIconSizes());
                  task.getIcon().set(layout.file("windows/app.ico"));
                  task.onlyIf(_ -> extension.getIcon().isPresent());
                });
    // The rendered icon, unless the build script names a ready-made one or has no icon at all.
    windows
        .getIcon()
        .convention(extension.getIcon().flatMap(_ -> icon.flatMap(GenerateWindowsIcon::getIcon)));

    TaskProvider<GenerateWindowsResourceScript> script =
        project
            .getTasks()
            .register(
                "generateWindowsResourceScript",
                GenerateWindowsResourceScript.class,
                task -> {
                  task.setDescription(
                      "Writes the icon and version resource script of the Windows executable.");
                  task.setGroup("build");
                  task.getIcon().set(windows.getIcon());
                  task.getFileDescription().set(windows.getFileDescription());
                  task.getProductName().set(windows.getProductName());
                  task.getCompanyName().set(windows.getCompanyName());
                  task.getCopyright().set(windows.getCopyright());
                  task.getVersion().set(windows.getVersion());
                  task.getOriginalFilename()
                      .set(extension.getImageName().map(name -> name + ".exe"));
                  task.getScript().set(layout.file("windows/app.rc"));
                });

    return project
        .getTasks()
        .register(
            "compileWindowsResources",
            CompileWindowsResources.class,
            task -> {
              task.setDescription("Compiles the Windows resource script with rc.exe.");
              task.setGroup("build");
              task.getScript()
                  .set(
                      windows
                          .getResourceScript()
                          .orElse(script.flatMap(GenerateWindowsResourceScript::getScript)));
              task.getResource().set(layout.file("windows/app.res"));
            });
  }

  /**
   * Registers {@code signWindowsExecutable}, which signs a copy of the executable when a
   * certificate is set.
   *
   * @return The executable that the installer packs: the signed copy, or the one of {@code
   *     nativeCompile} without a certificate.
   */
  private Provider<RegularFile> registerSigning(
      Project project, LwjwaeExtension extension, LwjwaeLayout layout) {
    WindowsExtension windows = extension.getWindows();
    WindowsSigningExtension signing = windows.getSigning();
    TaskProvider<SignWindowsExecutable> sign =
        project
            .getTasks()
            .register(
                "signWindowsExecutable",
                SignWindowsExecutable.class,
                task -> {
                  task.setDescription("Signs a copy of the Windows executable with signtool.");
                  task.setGroup("distribution");
                  task.dependsOn("nativeCompile");
                  task.onlyIf(_ -> Platform.isWindows());
                  task.getExecutable().set(layout.executable());
                  task.getCertificateFile().set(signing.getCertificateFile());
                  task.getCertificatePassword().set(signing.getCertificatePassword());
                  task.getCertificateThumbprint().set(signing.getCertificateThumbprint());
                  task.getTimestampUrl().set(signing.getTimestampUrl());
                  task.getFileDescription().set(windows.getFileDescription());
                  task.getSigned()
                      .set(
                          extension
                              .getImageName()
                              .flatMap(name -> layout.file("windows/signed/" + name + ".exe")));
                });
    Provider<Boolean> signed =
        signing
            .getCertificateFile()
            .map(_ -> true)
            .orElse(signing.getCertificateThumbprint().map(_ -> true))
            .orElse(false);
    return signed.flatMap(
        on -> on ? sign.flatMap(SignWindowsExecutable::getSigned) : layout.executable());
  }

  private void registerPackageMsi(
      Project project,
      LwjwaeExtension extension,
      LwjwaeLayout layout,
      Provider<RegularFile> executable) {
    MsiExtension msi = extension.getPackaging().getMsi();
    WindowsExtension windows = extension.getWindows();
    project
        .getTasks()
        .register(
            "packageMsi",
            PackageMsi.class,
            task -> {
              task.setDescription("Builds the Windows installer.");
              task.setGroup("distribution");
              task.dependsOn("nativeCompile");
              task.onlyIf(_ -> Platform.isWindows());
              task.getExecutable().set(executable);
              task.getIcon().set(windows.getIcon());
              task.getProductName().set(msi.getProductName());
              WindowsSigningExtension signing = windows.getSigning();
              task.getCertificateFile().set(signing.getCertificateFile());
              task.getCertificatePassword().set(signing.getCertificatePassword());
              task.getCertificateThumbprint().set(signing.getCertificateThumbprint());
              task.getTimestampUrl().set(signing.getTimestampUrl());
              task.getManufacturer().set(msi.getManufacturer());
              task.getVersion().set(windows.getVersion());
              task.getUpgradeCode().set(msi.getUpgradeCode());
              task.getPerUser().set(msi.getPerUser());
              task.getExecutableName().set(extension.getImageName().map(name -> name + ".exe"));
              task.getWixVersion().set(msi.getWixVersion());
              Associations.wire(task, extension);
              task.getTool().set(msi.getTool());
              task.getCacheDirectory().set(layout.toolCache());
              task.getWorkDirectory().set(layout.directory("msi"));
              task.getInstaller()
                  .set(
                      layout.distributionFile(
                          msi.getFileName()
                              .zip(
                                  layout.projectVersion(),
                                  (name, version) -> name + "-" + version + ".msi")));
            });
  }
}
