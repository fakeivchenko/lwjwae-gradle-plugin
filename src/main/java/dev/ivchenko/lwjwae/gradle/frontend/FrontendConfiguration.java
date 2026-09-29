package dev.ivchenko.lwjwae.gradle.frontend;

import dev.ivchenko.lwjwae.gradle.LwjwaeExtension;
import dev.ivchenko.lwjwae.gradle.LwjwaeLayout;
import java.util.List;
import java.util.concurrent.Callable;
import lombok.experimental.UtilityClass;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;

/**
 * Everything the plugin does for a frontend that npm builds: the defaults of {@code frontend {}},
 * {@code installFrontend}, {@code buildFrontend}, the page among the resources, and {@code runDev}.
 *
 * <p>The page reaches the resources through a directory of the plugin, {@code
 * build/lwjwae/frontend}, where {@code frontendResources} puts the output of the build under the
 * resource path. So the npm project keeps its own output directory, and the resources get only the
 * page, under the path that the application loads it from.
 */
@UtilityClass
public class FrontendConfiguration {
  /** Applies the defaults and registers the tasks. */
  public void configure(Project project, LwjwaeExtension extension, LwjwaeLayout layout) {
    FrontendExtension frontend = extension.getFrontend();
    frontend.getDirectory().convention(project.getLayout().getProjectDirectory().dir("frontend"));
    frontend
        .getEnabled()
        .convention(
            frontend
                .getDirectory()
                .map(directory -> directory.file("package.json").getAsFile().isFile()));
    frontend
        .getOutputDirectory()
        .convention(frontend.getDirectory().map(directory -> directory.dir("dist")));
    frontend.getResourcePath().convention("app");
    frontend.getBuildScript().convention("build");
    frontend.getDevScript().convention("dev");
    frontend.getDevServerUrl().convention("http://localhost:5173");
    Provider<Boolean> enabled = frontend.getEnabled();

    TaskProvider<InstallFrontend> install =
        project
            .getTasks()
            .register(
                "installFrontend",
                InstallFrontend.class,
                task -> {
                  task.setDescription("Installs the packages of the frontend with npm.");
                  task.setGroup("build");
                  task.onlyIf(_ -> enabled.get());
                  task.getDirectory().set(frontend.getDirectory());
                  task.getPackageJson().set(frontend.getDirectory().file("package.json"));
                  task.getLockFile()
                      .set(
                          frontend
                              .getDirectory()
                              .file("package-lock.json")
                              .filter(file -> file.getAsFile().isFile()));
                  task.getNodeModules().set(frontend.getDirectory().dir("node_modules"));
                });
    TaskProvider<BuildFrontend> build =
        project
            .getTasks()
            .register(
                "buildFrontend",
                BuildFrontend.class,
                task -> {
                  task.setDescription("Builds the page with npm.");
                  task.setGroup("build");
                  task.onlyIf(_ -> enabled.get());
                  task.dependsOn(install);
                  task.getDirectory().set(frontend.getDirectory());
                  task.getSources()
                      .from(
                          frontend
                              .getDirectory()
                              .map(
                                  directory ->
                                      directory
                                          .getAsFileTree()
                                          .matching(
                                              files ->
                                                  files.exclude(
                                                      "node_modules/**", "dist/**", "build/**"))));
                  task.getScript().set(frontend.getBuildScript());
                  task.getOutputDirectory().set(frontend.getOutputDirectory());
                });
    Provider<Directory> resources = layout.directory("frontend");
    TaskProvider<Sync> sync =
        project
            .getTasks()
            .register(
                "frontendResources",
                Sync.class,
                task -> {
                  task.setDescription("Puts the page into the resources under its resource path.");
                  task.onlyIf(_ -> enabled.get());
                  task.from(build.flatMap(BuildFrontend::getOutputDirectory));
                  task.into(resources.zip(frontend.getResourcePath(), Directory::dir));
                });

    // A collection rather than the directory itself: without a frontend, it adds nothing.
    ConfigurableFileCollection page =
        project.files((Callable<Object>) () -> enabled.get() ? resources.get() : List.of());
    page.builtBy(sync);
    project
        .getExtensions()
        .getByType(JavaPluginExtension.class)
        .getSourceSets()
        .named(SourceSet.MAIN_SOURCE_SET_NAME, sourceSet -> sourceSet.getResources().srcDir(page));

    project
        .getPluginManager()
        .withPlugin(
            "application",
            _ ->
                project
                    .getTasks()
                    .register(
                        "runDev",
                        RunDev.class,
                        task -> {
                          task.setDescription(
                              "Runs the application against the development server of the frontend,"
                                  + " with hot reload.");
                          task.setGroup("application");
                          task.onlyIf(_ -> enabled.get());
                          task.dependsOn(install);
                          JavaApplication application =
                              project.getExtensions().getByType(JavaApplication.class);
                          task.getMainClass().set(application.getMainClass());
                          task.getMainModule().set(application.getMainModule());
                          task.setClasspath(
                              project
                                  .getExtensions()
                                  .getByType(JavaPluginExtension.class)
                                  .getSourceSets()
                                  .getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                                  .getRuntimeClasspath());
                          task.getJvmArgumentProviders()
                              .add(application::getApplicationDefaultJvmArgs);
                          task.getDirectory().set(frontend.getDirectory());
                          task.getScript().set(frontend.getDevScript());
                          task.getDevServerUrl().set(frontend.getDevServerUrl());
                        }));
  }
}
