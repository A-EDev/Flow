/**
 * Robolectric defaults for every Compose test under {@code io.github.aedev.flow.ui}.
 *
 * <p>Conscrypt is off because {@code conscrypt-android} (an app dependency) shadows the host
 * Conscrypt Robolectric bundles and fails with {@code no conscrypt_jni in java.library.path};
 * the JDK's own TLS provider is enough for UI tests. NATIVE graphics gives real text measurement
 * so {@code assertIsDisplayed} sees non-zero bounds.
 */
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
package io.github.aedev.flow.ui;

import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;
