package io.github.aedev.flow.data.sponsordetection

import org.junit.Assume.assumeTrue
import java.io.File

private const val TOKENIZER_FILE = "tokenizer.json"

/**
 * Locates a local copy of the model package (`tokenizer.json` and `tokenizer_goldens.json`) from the
 * Hugging Face repository pinned in [SponsorModelConfig].
 *
 * The files are not part of this repository. Tests that need them are skipped unless the directory is
 * supplied via `-PsponsorModelAssets=<path>`, so a plain checkout and CI stay green.
 */
internal fun sponsorModelAssetsDirectory(): File {
    val configured = System.getProperty("sponsorModelAssets")
    assumeTrue(
        "Skipping: sponsor model package not provided. Re-run with " +
            "-PsponsorModelAssets=/path/to/downloaded/model/package",
        configured != null,
    )
    val directory = File(requireNotNull(configured))
    assumeTrue("Skipping: no $TOKENIZER_FILE under $directory", directory.resolve(TOKENIZER_FILE).isFile)
    return directory
}

internal fun sponsorModelAssetsFile(name: String): File = sponsorModelAssetsDirectory().resolve(name)

internal fun sponsorTestTokenizer(): SponsorTokenizer = SponsorTokenizer.fromJson(sponsorModelAssetsFile(TOKENIZER_FILE).readText())
