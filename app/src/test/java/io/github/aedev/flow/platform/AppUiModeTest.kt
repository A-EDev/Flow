package io.github.aedev.flow.platform

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppUiModeTest {
    @Test
    fun `automatic resolves to tv on television devices`() {
        assertThat(AppUiMode.AUTOMATIC.resolve(DeviceFormFactor.TV)).isEqualTo(AppUiRoot.TV)
    }

    @Test
    fun `automatic resolves to mobile on non television devices`() {
        assertThat(AppUiMode.AUTOMATIC.resolve(DeviceFormFactor.MOBILE)).isEqualTo(AppUiRoot.MOBILE)
    }

    @Test
    fun `manual modes override detected form factor`() {
        assertThat(AppUiMode.MOBILE.resolve(DeviceFormFactor.TV)).isEqualTo(AppUiRoot.MOBILE)
        assertThat(AppUiMode.TV.resolve(DeviceFormFactor.MOBILE)).isEqualTo(AppUiRoot.TV)
    }

    @Test
    fun `unknown stored value falls back to automatic`() {
        assertThat(AppUiMode.fromStorage("unknown")).isEqualTo(AppUiMode.AUTOMATIC)
        assertThat(AppUiMode.fromStorage(null)).isEqualTo(AppUiMode.AUTOMATIC)
    }
}
