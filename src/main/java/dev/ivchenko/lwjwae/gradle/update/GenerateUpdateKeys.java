package dev.ivchenko.lwjwae.gradle.update;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Prints a new pair of Ed25519 keys for signing updates, once for the life of an application: an
 * application trusts only the public key that it was built with, so a new pair locks the installed
 * versions out of later updates.
 */
@DisableCachingByDefault(because = "Makes a new key every time")
public abstract class GenerateUpdateKeys extends DefaultTask {
  /** Prints the keys. */
  @TaskAction
  public void generate() throws GeneralSecurityException {
    KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    Base64.Encoder base64 = Base64.getEncoder();
    this.getLogger()
        .quiet(
            """
            The public key, for lwjwae { updates { publicKey = "..." } }:

              {public}

            The private key, for the secret LWJWAE_UPDATE_PRIVATE_KEY of CI or the Gradle property
            lwjwae.updatePrivateKey. Keep it out of the repository, and keep a copy: without it,
            the installed versions can't be updated.

              {private}
            """
                .replace("{public}", base64.encodeToString(keys.getPublic().getEncoded()))
                .replace("{private}", base64.encodeToString(keys.getPrivate().getEncoded())));
  }
}
