package io.github.aedev.flow.player

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * A preference collector never finishes on its own, so each rebuild of the player used to add one
 * more set of them for the life of the process (#1148).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerBuildScopeTest {
    private val preference = MutableStateFlow(false)

    private fun CoroutineScope.collectForever(): Job = launch { preference.collect { } }

    @Test
    fun `rebuilding ends the previous build's collectors`() =
        runTest {
            val builds = PlayerBuildScope(backgroundScope)

            val first = builds.open().collectForever()
            runCurrent()
            val second = builds.open().collectForever()
            runCurrent()

            assertThat(first.isCancelled).isTrue()
            assertThat(second.isActive).isTrue()
            assertThat(preference.subscriptionCount.value).isEqualTo(1)
        }

    @Test
    fun `releasing ends the collectors and leaves the parent running`() =
        runTest {
            val builds = PlayerBuildScope(backgroundScope)
            val collectors = List(2) { builds.open().collectForever() }
            runCurrent()

            builds.close()
            runCurrent()

            assertThat(collectors.all { it.isCancelled }).isTrue()
            assertThat(preference.subscriptionCount.value).isEqualTo(0)
            assertThat(builds.isOpen).isFalse()
            assertThat(backgroundScope.isActive).isTrue()
        }

    @Test
    fun `one failing collector does not cancel its siblings`() =
        runTest {
            val built = PlayerBuildScope(backgroundScope).open()
            val sibling = built.collectForever()

            built.launch(CoroutineExceptionHandler { _, _ -> }) { error("boom") }
            runCurrent()

            assertThat(sibling.isActive).isTrue()
        }
}
