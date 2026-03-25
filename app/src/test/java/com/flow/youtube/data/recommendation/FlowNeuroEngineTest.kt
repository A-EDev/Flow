package io.github.aedev.flow.data.recommendation

import io.github.aedev.flow.data.model.Video
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Test
import java.util.Calendar

class FlowNeuroEngineTest {

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun createVideo(
        id: String,
        title: String,
        duration: Int = 300,
        isLive: Boolean = false,
        isShort: Boolean = false
    ) = Video(
        id = id,
        title = title,
        channelName = "Channel $id",
        channelId = "channel_$id",
        thumbnailUrl = "thumb",
        duration = duration,
        viewCount = 1000L,
        likeCount = 0L,
        uploadDate = "today",
        isLive = isLive,
        isShort = isShort
    )

    @Test
    fun `extractFeatures identifies topics from title`() {
        val video = createVideo("1", "Python Machine Learning Tutorial for Beginners")
        
        // Access private extractFeatures via reflection
        val method = FlowNeuroEngine::class.java.getDeclaredMethod("extractFeatures", Video::class.java)
        method.isAccessible = true
        val vector = method.invoke(FlowNeuroEngine, video) as ContentVector
        
        assertThat(vector.topics).containsKey("python")
        assertThat(vector.topics).containsKey("machine")
        assertThat(vector.topics).containsKey("learning")
        // Bigrams
        assertThat(vector.topics).containsKey("python machine")
        assertThat(vector.topics).containsKey("machine learning")
    }

    @Test
    fun `extractFeatures calculates duration score correctly`() {
        val longVideo = createVideo("long", "Long Video", duration = 1200) // 20 mins
        val shortVideo = createVideo("short", "Short Video", duration = 300) // 5 mins
        
        val method = FlowNeuroEngine::class.java.getDeclaredMethod("extractFeatures", Video::class.java)
        method.isAccessible = true
        
        val longVector = method.invoke(FlowNeuroEngine, longVideo) as ContentVector
        val shortVector = method.invoke(FlowNeuroEngine, shortVideo) as ContentVector
        
        assertThat(longVector.duration).isAtLeast(0.9)
        assertThat(shortVector.duration).isLessThan(0.5)
    }

    @Test
    fun `getPersona returns INITIATE for new users`() {
        val brain = UserBrain(totalInteractions = 5)
        val persona = FlowNeuroEngine.getPersona(brain)
        assertThat(persona).isEqualTo(FlowPersona.INITIATE)
    }

    @Test
    fun `getPersona returns AUDIOPHILE for music heavy users`() {
        val topics = mapOf("music" to 10.0, "lofi" to 5.0, "remix" to 5.0)
        val brain = UserBrain(
            globalVector = ContentVector(topics = topics),
            totalInteractions = 20
        )
        val persona = FlowNeuroEngine.getPersona(brain)
        assertThat(persona).isEqualTo(FlowPersona.AUDIOPHILE)
    }

    @Test
    fun `getPersona returns SCHOLAR for complex content`() {
        val topics = mapOf("quantum" to 5.0, "physics" to 5.0, "derivative" to 5.0)
        val brain = UserBrain(
            globalVector = ContentVector(topics = topics, complexity = 0.8),
            totalInteractions = 20
        )
        val persona = FlowNeuroEngine.getPersona(brain)
        assertThat(persona).isEqualTo(FlowPersona.SCHOLAR)
    }

    @Test
    fun `getPersona returns DEEP_DIVER for long form content`() {
        val topics = mapOf("essay" to 5.0, "documentary" to 5.0)
        val brain = UserBrain(
            globalVector = ContentVector(topics = topics, duration = 0.8),
            totalInteractions = 20
        )
        val persona = FlowNeuroEngine.getPersona(brain)
        assertThat(persona).isEqualTo(FlowPersona.DEEP_DIVER)
    }
}
