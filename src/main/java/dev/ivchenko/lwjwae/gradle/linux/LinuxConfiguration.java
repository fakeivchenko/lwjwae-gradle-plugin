package dev.ivchenko.lwjwae.gradle.linux;

import dev.ivchenko.lwjwae.gradle.LinuxBackend;
import dev.ivchenko.lwjwae.gradle.LwjwaeExtension;
import dev.ivchenko.lwjwae.gradle.LwjwaeLayout;
import dev.ivchenko.lwjwae.gradle.PackagingExtension;
import dev.ivchenko.lwjwae.gradle.macos.MacOsConfiguration;
import dev.ivchenko.lwjwae.gradle.util.Platform;
import lombok.experimental.UtilityClass;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

/**
 * Everything the plugin does for Linux: the defaults of {@code packaging.deb {}} and {@code
 * packaging.appImage {}}, and {@code packageDeb} and {@code packageAppImage}. The executable itself
 * needs nothing that other platforms don't.
 */
@UtilityClass
public class LinuxConfiguration {
  /** Applies the defaults and registers the tasks. */
  public void configure(Project project, LwjwaeExtension extension, LwjwaeLayout layout) {
    LinuxConfiguration.applyDefaults(extension, layout);
    LinuxConfiguration.registerPackageDeb(project, extension, layout);
    LinuxConfiguration.registerPackageAppImage(project, extension, layout);
  }

  private void applyDefaults(LwjwaeExtension extension, LwjwaeLayout layout) {
    PackagingExtension packaging = extension.getPackaging();
    DebExtension deb = packaging.getDeb();
    deb.getEnabled().convention(false);
    deb.getPackageName().convention(extension.getImageName().map(PackageDeb::packageName));
    deb.getMaintainer().convention(extension.getVendor().filter(vendor -> vendor.contains("<")));
    deb.getDepends().convention(extension.getLinuxBackend().map(LinuxBackend::packages));
    deb.getSection().convention("utils");
    deb.getPriority().convention("optional");
    deb.getVersion().convention(layout.projectVersion());

    AppImageExtension appImage = packaging.getAppImage();
    appImage.getEnabled().convention(false);
    appImage.getFileName().convention(extension.getImageName());
  }

  private void registerPackageDeb(Project project, LwjwaeExtension extension, LwjwaeLayout layout) {
    PackagingExtension packaging = extension.getPackaging();
    DebExtension deb = packaging.getDeb();
    project
        .getTasks()
        .register(
            "packageDeb",
            PackageDeb.class,
            task -> {
              task.setDescription("Builds the Debian package.");
              task.setGroup("distribution");
              task.dependsOn("nativeCompile");
              task.onlyIf(_ -> Platform.isLinux());
              task.getExecutable().set(layout.executable());
              task.getIcon().set(extension.getIcon());
              task.getIconName().set(LinuxConfiguration.iconName(project, extension));
              task.getPackageName().set(deb.getPackageName());
              task.getDisplayName().set(extension.getDisplayName());
              task.getVersion().set(deb.getVersion());
              task.getArchitecture().set(PackageDeb.architecture());
              task.getMaintainer().set(deb.getMaintainer());
              task.getSummary().set(packaging.getDescription());
              task.getHomepage().set(packaging.getHomepage());
              task.getSection().set(deb.getSection());
              task.getPriority().set(deb.getPriority());
              task.getDepends().set(deb.getDepends());
              task.getCategories().set(packaging.getCategories());
              task.getPackageFile()
                  .set(
                      layout.distributionFile(
                          deb.getPackageName()
                              .zip(
                                  deb.getVersion(),
                                  (name, version) ->
                                      name
                                          + "_"
                                          + version
                                          + "_"
                                          + PackageDeb.architecture()
                                          + ".deb")));
            });
  }

  private void registerPackageAppImage(
      Project project, LwjwaeExtension extension, LwjwaeLayout layout) {
    PackagingExtension packaging = extension.getPackaging();
    AppImageExtension appImage = packaging.getAppImage();
    project
        .getTasks()
        .register(
            "packageAppImage",
            PackageAppImage.class,
            task -> {
              task.setDescription("Builds the AppImage.");
              task.setGroup("distribution");
              task.dependsOn("nativeCompile");
              task.onlyIf(_ -> Platform.isLinux());
              task.getExecutable().set(layout.executable());
              task.getIcon().set(extension.getIcon());
              task.getIconName().set(LinuxConfiguration.iconName(project, extension));
              task.getDisplayName().set(extension.getDisplayName());
              task.getImageName().set(extension.getImageName());
              task.getSummary().set(packaging.getDescription());
              task.getCategories().set(packaging.getCategories());
              task.getTool().set(appImage.getTool());
              task.getCacheDirectory().set(layout.toolCache());
              task.getAppDir().set(layout.directory("appimage/AppDir"));
              task.getAppImage()
                  .set(
                      layout.distributionFile(
                          appImage
                              .getFileName()
                              .zip(
                                  layout.projectVersion(),
                                  (name, version) ->
                                      name
                                          + "-"
                                          + version
                                          + "-"
                                          + PackageAppImage.architecture()
                                          + ".AppImage")));
            });
  }

  /** {@code GROUP.NAME} of the image, as the name of its icon. */
  private Provider<String> iconName(Project project, LwjwaeExtension extension) {
    return extension
        .getImageName()
        .map(name -> MacOsConfiguration.bundleIdentifier(project.getGroup().toString(), name));
  }
}
