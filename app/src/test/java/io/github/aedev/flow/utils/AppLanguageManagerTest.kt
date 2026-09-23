package io.github.aedev.flow.utils

import android.app.Application
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS-notnight")
@ConscryptMode(ConscryptMode.Mode.OFF)
class AppLanguageManagerTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun Configuration.isNight() = (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    @Test
    fun `following the system language leaves the context unwrapped`() {
        assertSame(app, AppLanguageManager.wrapContext(app, AppLanguageManager.SYSTEM_DEFAULT))
    }

    @Test
    fun `a chosen language applies its locale`() {
        val wrapped = AppLanguageManager.wrapContext(app, "de")
        assertEquals(
            "de",
            wrapped.resources.configuration.locales[0]
                .language,
        )
    }

    @Test
    fun `a chosen language still follows the system night mode (#972)`() {
        val wrapped = AppLanguageManager.wrapContext(app, "de")
        RuntimeEnvironment.setQualifiers("+night")
        assertEquals(true, app.resources.configuration.isNight())
        assertEquals(true, wrapped.resources.configuration.isNight())
    }
}
