package dev.ivchenko.lwjwae.gradle;

/** The backends that the plugin puts on the runtime classpath. */
public enum Backends {
  /**
   * The backend of the operating system that runs the build: the right one for a native image built
   * on this machine.
   */
  CURRENT_PLATFORM,

  /**
   * Both GTK backends, WebView2, and WKWebView together: a JAR file that runs on any of the three
   * systems. Where both WebKitGTK versions are installed, the GTK 3 backend wins.
   */
  ALL,

  /** No backend. The build script declares its own. */
  NONE
}
