package io.github.aedev.flow.innertube

import io.github.aedev.flow.utils.protobuf.ProtobufWriter
import java.net.URLEncoder
import java.util.Base64

internal object YouTubeSearchParams {
    enum class ContentType(val value: Int) {
        VIDEO(1),
        CHANNEL(2),
        PLAYLIST(3),
    }

    enum class Duration(val value: Int) {
        SHORT(1),
        LONG(2),
        MEDIUM(3),
    }

    enum class UploadDate(val value: Int) {
        TODAY(2),
        THIS_WEEK(3),
        THIS_MONTH(4),
        THIS_YEAR(5),
    }

    fun sortedByViewCount(
        contentType: ContentType? = null,
        duration: Duration? = null,
        uploadDate: UploadDate? = null,
        liveOnly: Boolean = false,
    ): String {
        val filters = ProtobufWriter.encode {
            uploadDate?.let { writeInt32(FILTER_DATE_FIELD, it.value) }
            contentType?.let { writeInt32(FILTER_TYPE_FIELD, it.value) }
            duration?.let { writeInt32(FILTER_DURATION_FIELD, it.value) }
            if (liveOnly) writeBool(FILTER_LIVE_FIELD, true)
        }

        val request = ProtobufWriter.encode {
            writeInt32(SORT_FIELD, SORT_BY_VIEW_COUNT)
            if (filters.isNotEmpty()) writeBytes(FILTERS_FIELD, filters)
        }

        val encodedRequest = Base64.getEncoder().encodeToString(request)
        return URLEncoder.encode(encodedRequest, Charsets.UTF_8.name())
    }

    private const val SORT_FIELD = 1
    private const val FILTERS_FIELD = 2
    private const val FILTER_DATE_FIELD = 1
    private const val FILTER_TYPE_FIELD = 2
    private const val FILTER_DURATION_FIELD = 3
    private const val FILTER_LIVE_FIELD = 8
    private const val SORT_BY_VIEW_COUNT = 3
}
