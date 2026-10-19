package dev.ivchenko.lwjwae.gradle;

/**
 * The versions of lwjwae that a project builds with.
 *
 * @param library The version of {@code lwjwae-core} and the backends.
 * @param codecs The version of the codec modules.
 */
record Versions(String library, String codecs) {}
