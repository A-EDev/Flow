package io.github.aedev.flow.data.sponsordetection

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Installs the exported model bundled as instrumentation-test assets into the
 * target app's files directory so device tests exercise the same on-disk layout
 * the runtime downloader produces.
 */
internal fun installSponsorModelFromTestAssets(context: Context) {
    val store = SponsorModelStore(context)
    store.resetStaging()
    val assets = InstrumentationRegistry.getInstrumentation().context.assets
    assets.open(SponsorModelConfig.MODEL_FILE_NAME).use { input ->
        store.stagedModelFile().outputStream().use(input::copyTo)
    }
    assets.open(SponsorModelConfig.TOKENIZER_FILE_NAME).use { input ->
        store.stagedTokenizerFile().outputStream().use(input::copyTo)
    }
    store.installStaged()
}
