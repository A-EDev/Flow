/*
 * Pure-Kotlin Shazam audio fingerprint generator. Ported from the vibra/SongRec
 * algorithm (https://github.com/marin-m/SongRec) via Metrolist (GPL-3.0); replaces the
 * native C++ + FFTW3 implementation with a JVM-only FFT. Byte layout is wire-compatible
 * with the Shazam DejaVu signature format, so the magic numbers below must not change.
 */
package io.github.aedev.flow.data.recognition.signature

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max

internal object ShazamSignatureGenerator {

    private const val SAMPLE_RATE = 16_000
    private const val FFT_SIZE = 2048
    private const val FFT_OUTPUT_SIZE = FFT_SIZE / 2 + 1  // 1025
    private const val MAX_PEAKS = 255
    private const val MAX_TIME_SECONDS = 12.0
    private const val RING_BUF_SIZE = 256

    private const val BAND_250_520 = 0
    private const val BAND_520_1450 = 1
    private const val BAND_1450_3500 = 2
    private const val BAND_3500_5500 = 3

    // Hanning window matching the precomputed C++ HANNIG_MATRIX values.
    private val HANNING = DoubleArray(FFT_SIZE) { i ->
        0.5 * (1.0 - cos(2.0 * PI * (i + 1).toDouble() / 2049.0))
    }

    fun fromI16(samples: ByteArray): String {
        require(samples.size >= 2 && samples.size % 2 == 0) {
            "samples must be a non-empty byte array with even length (16-bit PCM)"
        }
        val pcm = ShortArray(samples.size / 2)
        ByteBuffer.wrap(samples).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm)
        return SignatureGeneratorState().process(pcm)
    }

    private class SignatureGeneratorState {
        private val samplesRing = IntArray(FFT_SIZE)
        private var samplesPos = 0

        private val fftOutputs = Array(RING_BUF_SIZE) { DoubleArray(FFT_OUTPUT_SIZE) }
        private var fftPos = 0

        private val spreadFfts = Array(RING_BUF_SIZE) { DoubleArray(FFT_OUTPUT_SIZE) }
        private var spreadPos = 0
        private var spreadNumWritten = 0

        private var numSamples = 0

        private val bandPeaks = Array(4) { mutableListOf<FrequencyPeak>() }
        private var totalPeaks = 0

        fun process(pcm: ShortArray): String {
            var offset = 0
            while (offset + 128 <= pcm.size) {
                // Stop only once BOTH the time and peak budgets are exhausted (matches C++).
                val elapsedSec = numSamples.toDouble() / SAMPLE_RATE
                if (elapsedSec >= MAX_TIME_SECONDS && totalPeaks >= MAX_PEAKS) break

                numSamples += 128
                feedSamples(pcm, offset, 128)
                doFFT()
                doPeakSpreading()
                if (spreadNumWritten >= 47) doPeakRecognition()
                offset += 128
            }
            return encodeSignature()
        }

        private fun feedSamples(pcm: ShortArray, start: Int, count: Int) {
            for (k in start until start + count) {
                samplesRing[samplesPos] = pcm[k].toInt()
                samplesPos = (samplesPos + 1) % FFT_SIZE
            }
        }

        private fun doFFT() {
            val windowed = DoubleArray(FFT_SIZE) { i ->
                samplesRing[(samplesPos + i) % FFT_SIZE].toDouble() * HANNING[i]
            }
            computeRfft(windowed).copyInto(fftOutputs[fftPos])
            fftPos = (fftPos + 1) % RING_BUF_SIZE
        }

        private fun doPeakSpreading() {
            val lastFftIdx = (fftPos - 1 + RING_BUF_SIZE) % RING_BUF_SIZE
            val spread = fftOutputs[lastFftIdx].copyOf()

            // Frequency spreading: 3-point running max (forward pass).
            for (pos in 0 until FFT_OUTPUT_SIZE - 2) {
                spread[pos] = maxOf(spread[pos], spread[pos + 1], spread[pos + 2])
            }

            // Time spreading: propagate max into older entries at offsets -1, -3, -6.
            for (pos in 0 until FFT_OUTPUT_SIZE) {
                var maxVal = spread[pos]
                for (offset in intArrayOf(-1, -3, -6)) {
                    val idx = ((spreadPos + offset) % RING_BUF_SIZE + RING_BUF_SIZE) % RING_BUF_SIZE
                    val oldVal = spreadFfts[idx][pos]
                    if (oldVal > maxVal) maxVal = oldVal
                    spreadFfts[idx][pos] = maxVal
                }
                // The new entry keeps only frequency spreading, matching the C++ implementation.
            }

            spread.copyInto(spreadFfts[spreadPos])
            spreadPos = (spreadPos + 1) % RING_BUF_SIZE
            spreadNumWritten++
        }

        private fun doPeakRecognition() {
            val fftMinus46 = fftOutputs[(fftPos - 46 + RING_BUF_SIZE * 2) % RING_BUF_SIZE]
            val spreadMinus49 = spreadFfts[(spreadPos - 49 + RING_BUF_SIZE * 2) % RING_BUF_SIZE]
            val otherOffsets = intArrayOf(-53, -45, 165, 172, 179, 186, 193, 200, 214, 221, 228, 235, 242, 249)

            for (binPos in 10 until FFT_OUTPUT_SIZE - 8) {
                val fftVal = fftMinus46[binPos]
                if (fftVal < 1.0 / 64.0 || fftVal < spreadMinus49[binPos]) continue

                var maxNeighborSpread49 = 0.0
                for (neighborOffset in intArrayOf(-10, -7, -4, -3, 1, 2, 5, 8)) {
                    val v = spreadMinus49[binPos + neighborOffset]
                    if (v > maxNeighborSpread49) maxNeighborSpread49 = v
                }
                if (fftVal <= maxNeighborSpread49) continue

                var maxNeighborOther = maxNeighborSpread49
                for (otherOffset in otherOffsets) {
                    val spreadIdx = ((spreadPos + otherOffset) % RING_BUF_SIZE + RING_BUF_SIZE) % RING_BUF_SIZE
                    val v = spreadFfts[spreadIdx][binPos - 1]
                    if (v > maxNeighborOther) maxNeighborOther = v
                }
                if (fftVal <= maxNeighborOther) continue

                val fftNumber = spreadNumWritten - 46
                val peakMag = ln(max(1.0 / 64.0, fftVal)) * 1477.3 + 6144
                val peakMagBefore = ln(max(1.0 / 64.0, fftMinus46[binPos - 1])) * 1477.3 + 6144
                val peakMagAfter = ln(max(1.0 / 64.0, fftMinus46[binPos + 1])) * 1477.3 + 6144

                val peakVariation1 = peakMag * 2 - peakMagBefore - peakMagAfter
                val peakVariation2 = (peakMagAfter - peakMagBefore) * 32 / peakVariation1
                val correctedBin = binPos * 64.0 + peakVariation2
                val frequencyHz = correctedBin * (16000.0 / 2.0 / 1024.0 / 64.0)

                val band = when {
                    frequencyHz < 250.0 -> continue
                    frequencyHz < 520.0 -> BAND_250_520
                    frequencyHz < 1450.0 -> BAND_520_1450
                    frequencyHz < 3500.0 -> BAND_1450_3500
                    frequencyHz <= 5500.0 -> BAND_3500_5500
                    else -> continue
                }

                bandPeaks[band].add(
                    FrequencyPeak(fftNumber, peakMag.toInt(), correctedBin.toInt())
                )
                totalPeaks++
            }
        }

        private fun encodeSignature(): String {
            val contentsStream = ByteArrayOutputStream()

            for (bandId in 0..3) {
                val peaks = bandPeaks[bandId]
                if (peaks.isEmpty()) continue

                val peakBuf = ByteArrayOutputStream()
                var prevFftPassNumber = 0
                for (peak in peaks) {
                    val diff = peak.fftPassNumber - prevFftPassNumber
                    if (diff >= 255) {
                        peakBuf.write(0xFF)
                        writeLittleEndian32(peakBuf, peak.fftPassNumber)
                        prevFftPassNumber = peak.fftPassNumber
                    }
                    peakBuf.write(peak.fftPassNumber - prevFftPassNumber)
                    writeLittleEndian16(peakBuf, peak.peakMagnitude)
                    writeLittleEndian16(peakBuf, peak.correctedPeakFrequencyBin)
                    prevFftPassNumber = peak.fftPassNumber
                }

                val peakBytes = peakBuf.toByteArray()
                writeLittleEndian32(contentsStream, 0x60030040 + bandId)
                writeLittleEndian32(contentsStream, peakBytes.size)
                contentsStream.write(peakBytes)

                val padBytes = (4 - peakBytes.size % 4) % 4
                repeat(padBytes) { contentsStream.write(0) }
            }

            val contents = contentsStream.toByteArray()
            val sizeMinusHeader = contents.size + 8
            val samplesAndOffset = (numSamples + SAMPLE_RATE * 0.24).toInt()

            val headerBytes = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(0xcafe2580.toInt())
                putInt(0)                       // crc32 placeholder
                putInt(sizeMinusHeader)
                putInt(0x94119c00.toInt())
                putInt(0); putInt(0); putInt(0)
                putInt(3 shl 27)                // shifted_sample_rate_id
                putInt(0); putInt(0)
                putInt(samplesAndOffset)
                putInt((15 shl 19) + 0x40000)
            }.array()

            val fullBuf = ByteArrayOutputStream(56 + contents.size)
            fullBuf.write(headerBytes)
            writeLittleEndian32(fullBuf, 0x40000000)
            writeLittleEndian32(fullBuf, contents.size + 8)
            fullBuf.write(contents)

            val fullBytes = fullBuf.toByteArray()
            val crc = CRC32()
            crc.update(fullBytes, 8, fullBytes.size - 8)
            val crc32Value = crc.value.toInt()
            fullBytes[4] = (crc32Value and 0xFF).toByte()
            fullBytes[5] = ((crc32Value shr 8) and 0xFF).toByte()
            fullBytes[6] = ((crc32Value shr 16) and 0xFF).toByte()
            fullBytes[7] = ((crc32Value shr 24) and 0xFF).toByte()

            return "data:audio/vnd.shazam.sig;base64,${Base64.encodeToString(fullBytes, Base64.NO_WRAP)}"
        }
    }

    private data class FrequencyPeak(
        val fftPassNumber: Int,
        val peakMagnitude: Int,
        val correctedPeakFrequencyBin: Int
    )

    private fun writeLittleEndian32(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 24) and 0xFF)
    }

    private fun writeLittleEndian16(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }

    // Iterative radix-2 Cooley-Tukey FFT; returns 1025 magnitudes (FFTW3 r2c-compatible).
    private fun computeRfft(windowed: DoubleArray): DoubleArray {
        val n = windowed.size
        val re = windowed.copyOf()
        val im = DoubleArray(n)

        var j = 0
        for (i in 1 until n) {
            var bit = n ushr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit ushr 1
            }
            j = j xor bit
            if (i < j) {
                var tmp = re[i]; re[i] = re[j]; re[j] = tmp
                tmp = im[i]; im[i] = im[j]; im[j] = tmp
            }
        }

        var len = 2
        while (len <= n) {
            val halfLen = len ushr 1
            val ang = -PI / halfLen
            val wBaseRe = cos(ang)
            val wBaseIm = kotlin.math.sin(ang)
            var i = 0
            while (i < n) {
                var wRe = 1.0
                var wIm = 0.0
                for (k in 0 until halfLen) {
                    val u = i + k
                    val v = u + halfLen
                    val evenRe = re[u]
                    val evenIm = im[u]
                    val oddRe = re[v] * wRe - im[v] * wIm
                    val oddIm = re[v] * wIm + im[v] * wRe
                    re[u] = evenRe + oddRe
                    im[u] = evenIm + oddIm
                    re[v] = evenRe - oddRe
                    im[v] = evenIm - oddIm
                    val newWRe = wRe * wBaseRe - wIm * wBaseIm
                    wIm = wRe * wBaseIm + wIm * wBaseRe
                    wRe = newWRe
                }
                i += len
            }
            len = len shl 1
        }

        val scaleFactor = 1.0 / (1 shl 17)
        val minVal = 1e-10
        return DoubleArray(FFT_OUTPUT_SIZE) { idx ->
            val r = re[idx]
            val img = im[idx]
            val mag = (r * r + img * img) * scaleFactor
            if (mag < minVal) minVal else mag
        }
    }
}
