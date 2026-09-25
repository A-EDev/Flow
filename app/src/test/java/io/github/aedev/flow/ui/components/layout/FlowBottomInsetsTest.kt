package io.github.aedev.flow.ui.components.layout

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FlowBottomInsetsTest {
    private val system = mutableStateOf(24.dp)
    private val bar = mutableStateOf(64.dp)
    private val barShown = mutableStateOf(true)
    private val miniShown = mutableStateOf(true)
    private val barFraction = mutableFloatStateOf(1f)
    private val miniFraction = mutableFloatStateOf(1f)
    private val insets =
        FlowBottomInsets(
            systemInset = system,
            barHeight = bar,
            barShown = barShown,
            miniPlayerHeight = mutableStateOf(72.dp),
            miniPlayerShown = miniShown,
            barFraction = { barFraction.floatValue },
            miniPlayerFraction = { miniFraction.floatValue },
        )
    private val density = Density(2f)

    @Test
    fun `a list clears the gesture area, the bar and the mini player`() {
        assertThat(insets.contentBottom).isEqualTo(160.dp)
        assertThat(insets.chromeAboveSystem).isEqualTo(136.dp)
    }

    @Test
    fun `a hidden bar and a dismissed mini player leave only the gesture area`() {
        barShown.value = false
        miniShown.value = false
        assertThat(insets.contentBottom).isEqualTo(24.dp)
        assertThat(insets.barBottom).isEqualTo(0.dp)
    }

    @Test
    fun `the mini player rides on the bar while it slides`() {
        barFraction.floatValue = 0.5f
        assertThat(insets.miniPlayerBaselinePx(density)).isEqualTo((24f + 32f) * 2f)
        assertThat(insets.floatingBottomPx(density)).isEqualTo((24f + 32f + 72f) * 2f)
    }

    @Test
    fun `floating content follows the mini player as it appears`() {
        barFraction.floatValue = 0f
        miniFraction.floatValue = 0.25f
        assertThat(insets.floatingBottomPx(density)).isEqualTo((24f + 18f) * 2f)
    }

    @Test
    fun `outside the app shell nothing is reserved`() {
        assertThat(FlowBottomInsets.None.contentBottom).isEqualTo(0.dp)
        assertThat(FlowBottomInsets.None.floatingBottomPx(density)).isEqualTo(0f)
    }
}
