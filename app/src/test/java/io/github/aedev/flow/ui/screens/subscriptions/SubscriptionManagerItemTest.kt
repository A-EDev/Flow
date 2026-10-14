package io.github.aedev.flow.ui.screens.subscriptions

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Channel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp")
class SubscriptionManagerItemTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val channel =
        Channel(
            id = "test-channel",
            name = "A channel with a long name that must remain readable",
            thumbnailUrl = "",
            subscriberCount = 0,
            isSubscribed = true,
        )

    private fun show(
        item: Channel = channel,
        fontScale: Float = 1f,
        direction: LayoutDirection = LayoutDirection.Ltr,
        onOpen: () -> Unit = {},
        onUnsubscribe: () -> Unit = {},
        onShortsChange: (Boolean) -> Unit = {},
    ) {
        rule.setContent {
            val density = LocalDensity.current
            var notifications by remember { mutableStateOf(false) }
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalLayoutDirection provides direction,
            ) {
                MaterialTheme {
                    Box(Modifier.width(320.dp)) {
                        SubscriptionManagerItem(
                            channel = item,
                            onClick = onOpen,
                            onUnsubscribe = onUnsubscribe,
                            isNotificationsEnabled = notifications,
                            areShortsExcluded = false,
                            onNotificationChange = { notifications = it },
                            onShortsExcludeChange = onShortsChange,
                        )
                    }
                }
            }
        }
    }

    private fun assertNameHasSpace(
        name: String,
        direction: LayoutDirection,
    ) {
        val title = rule.onNodeWithText(name, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val bellNode = rule.onNodeWithContentDescription(context.getString(R.string.notifications)).fetchSemanticsNode()
        val menuNode = rule.onNodeWithContentDescription(context.getString(R.string.subscribed)).fetchSemanticsNode()
        val bell = bellNode.boundsInRoot
        val menu = menuNode.boundsInRoot

        assertThat(title.width).isGreaterThan(bell.width + menu.width)
        if (direction == LayoutDirection.Ltr) {
            assertThat(title.right).isLessThan(minOf(bell.left, menu.left))
        } else {
            assertThat(title.left).isGreaterThan(maxOf(bell.right, menu.right))
        }
        val minimumTouchSize = with(rule.density) { 48.dp.toPx() }
        listOf(bellNode.touchBoundsInRoot, menuNode.touchBoundsInRoot).forEach { bounds ->
            assertThat(bounds.width).isAtLeast(minimumTouchSize)
            assertThat(bounds.height).isAtLeast(minimumTouchSize)
        }
    }

    @Test
    fun `long channel names keep readable space beside the controls`() {
        show()

        assertNameHasSpace(channel.name, LayoutDirection.Ltr)
        rule.onNodeWithText(context.getString(R.string.subscribed)).assertDoesNotExist()
    }

    @Test
    fun `large text and rtl music subscriptions keep the name clear of the controls`() {
        val musicChannel = channel.copy(name = "قناة موسيقية ذات اسم طويل لاختبار مساحة العرض", isMusic = true)
        show(item = musicChannel, fontScale = 2f, direction = LayoutDirection.Rtl)

        assertNameHasSpace(musicChannel.name, LayoutDirection.Rtl)
        rule.onNodeWithText(context.getString(R.string.subscriptions_music_chip_label)).assertIsDisplayed()
    }

    @Test
    fun `notification toggle does not open or unsubscribe the channel`() {
        var opened = false
        var unsubscribed = false
        show(onOpen = { opened = true }, onUnsubscribe = { unsubscribed = true })
        val bell = rule.onNodeWithContentDescription(context.getString(R.string.notifications))

        bell.assertIsOff().performClick()
        bell.assertIsOn().performClick()
        bell.assertIsOff()
        assertThat(opened).isFalse()
        assertThat(unsubscribed).isFalse()
    }

    @Test
    fun `menu preserves shorts filtering and requires a separate unsubscribe action`() {
        var excluded: Boolean? = null
        var unsubscribed = false
        show(onUnsubscribe = { unsubscribed = true }, onShortsChange = { excluded = it })
        val menu = rule.onNodeWithContentDescription(context.getString(R.string.subscribed))

        menu.performClick()
        assertThat(unsubscribed).isFalse()
        rule.onNodeWithText(context.getString(R.string.hide_channel_shorts)).performClick()
        assertThat(excluded).isTrue()
        rule.onNodeWithText(context.getString(R.string.hide_channel_shorts)).assertDoesNotExist()

        menu.performClick()
        assertThat(unsubscribed).isFalse()
        rule.onNodeWithText(context.getString(R.string.unsubscribe)).performClick()
        assertThat(unsubscribed).isTrue()
    }
}
