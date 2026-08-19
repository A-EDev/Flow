package io.github.aedev.flow.utils.potoken

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisitorIdTest {
    /** A real visitorData captured from youtube.com; its visitor ID is `rlqDISqy6-0`. */
    private val realVisitorData =
        "CgtybHFESVNxeTYtMCjA9pLUBjIKCgJMQhIEGgAgP2LfAgrcAjIxLllUPWhpRGRwQmZwWFRUNU9WRkJT"

    @Test
    fun `extracts the visitor id from real visitor data`() {
        assertEquals("rlqDISqy6-0", VisitorId.extract(realVisitorData))
    }

    @Test
    fun `url-encoded padding does not defeat extraction`() {
        assertEquals("rlqDISqy6-0", VisitorId.extract("$realVisitorData%3D%3D"))
    }

    @Test
    fun `gvsBinding prefers the visitor id over the blob`() {
        val binding = VisitorId.gvsBinding(realVisitorData)
        assertEquals("rlqDISqy6-0", binding)
        // The whole point: GVS binds to the short id, not the several-hundred-character blob.
        assertEquals(11, binding.length)
    }

    @Test
    fun `gvsBinding falls back to the raw value when no id can be read`() {
        val notProtobuf = "not-base64-at-all!!"
        assertEquals(notProtobuf, VisitorId.gvsBinding(notProtobuf))
    }

    @Test
    fun `blank and malformed input yield null`() {
        assertNull(VisitorId.extract(null))
        assertNull(VisitorId.extract(""))
        assertNull(VisitorId.extract("   "))
        // Valid base64, but too short to contain an id.
        assertNull(VisitorId.extract("CgQ="))
    }
}
