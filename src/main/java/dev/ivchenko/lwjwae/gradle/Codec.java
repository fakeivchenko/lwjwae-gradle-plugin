package dev.ivchenko.lwjwae.gradle;

/** The codec that the plugin adds for the typed bridge: typed bindings and object events. */
public enum Codec {
  /** No codec. The typed bridge is unavailable unless the build script adds a codec itself. */
  NONE,

  /** {@code lwjwae-codec-jackson}: Jackson Databind. */
  JACKSON,

  /** {@code lwjwae-codec-gson}: Gson. */
  GSON,

  /**
   * {@code lwjwae-codec-jsonb}: Jakarta JSON Binding with an implementation, see {@code
   * jsonbProvider}.
   */
  JSONB
}
