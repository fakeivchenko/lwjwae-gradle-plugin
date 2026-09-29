package dev.ivchenko.lwjwae.gradle.frontend;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code frontend {}} block: a page that npm builds, such as a Vite project, which the plugin
 * installs, builds into the resources of the application, and serves with hot reload for {@code
 * runDev}.
 *
 * <p>A project with {@code frontend/package.json} needs no configuration: the plugin finds it
 * there, runs {@code npm ci}, or {@code npm install} without a lock file, then {@code npm run
 * build}, and puts {@code frontend/dist} into the resources under {@code app/}, where {@code
 * window.loadResource("app/index.html")} finds it. npm comes from the {@code PATH}.
 */
public abstract class FrontendExtension {
  /** Whether the plugin builds the frontend. Default: whether the directory has a package.json. */
  public abstract Property<Boolean> getEnabled();

  /** The npm project. Default: {@code frontend}. */
  public abstract DirectoryProperty getDirectory();

  /** Where the build script writes the page. Default: {@code dist} in the npm project. */
  public abstract DirectoryProperty getOutputDirectory();

  /** Where the page goes among the resources. Default: {@code app}. */
  public abstract Property<String> getResourcePath();

  /** The script of {@code package.json} that builds the page. Default: {@code build}. */
  public abstract Property<String> getBuildScript();

  /** The script that starts the development server. Default: {@code dev}. */
  public abstract Property<String> getDevScript();

  /**
   * Where the development server serves the page, which {@code runDev} waits for and loads.
   * Default: {@code http://localhost:5173}, the one of Vite.
   */
  public abstract Property<String> getDevServerUrl();
}
