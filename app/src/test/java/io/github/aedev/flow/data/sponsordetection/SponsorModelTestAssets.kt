package io.github.aedev.flow.data.sponsordetection

import org.junit.Assume.assumeTrue
import java.io.File

private const val TOKENIZER_FILE = "tokenizer.json"

/**
 * Locates the Android model export produced by the Flow-SponsorML repository.
 *
 * The export is not part of this repository: the tokenizer and its goldens only exist after
 * `sponsor-detection export-android` has run there. Tests that need them are skipped unless the
 * directory is supplied via `-PsponsorModelAssets=<path>`, so a plain checkout and CI stay green
 * while ML work can still exercise the real tokenizer.
 */
internal fun sponsorModelAssetsDirectory(): File {
    val configured = System.getProperty("sponsorModelAssets")
    assumeTrue(
        "Skipping: sponsor model export not provided. Re-run with " +
            "-PsponsorModelAssets=/path/to/Flow-SponsorML/artifacts/android/<export>/android",
        configured != null,
    )
    val directory = File(requireNotNull(configured))
    assumeTrue("Skipping: no $TOKENIZER_FILE under $directory", directory.resolve(TOKENIZER_FILE).isFile)
    return directory
}

internal fun sponsorModelAssetsFile(name: String): File = sponsorModelAssetsDirectory().resolve(name)

internal fun sponsorTestTokenizer(): SponsorTokenizer = SponsorTokenizer.fromJson(sponsorModelAssetsFile(TOKENIZER_FILE).readText())
