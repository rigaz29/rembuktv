package com.rembuk.rembuktv

import com.rembuk.rembuktv.data.parser.resolveStreamType
import com.rembuk.rembuktv.data.parser.stableChannelId
import com.rembuk.rembuktv.domain.model.StreamType
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamTypeResolverTest {

    @Test
    fun `honors explicit declaration`() {
        assertEquals(StreamType.DASH, resolveStreamType("dash", "http://x/stream"))
        assertEquals(StreamType.DASH, resolveStreamType("mpd", "http://x/stream"))
        assertEquals(StreamType.HLS, resolveStreamType("HLS", "http://x/stream"))
        assertEquals(StreamType.HLS, resolveStreamType("m3u8", "http://x/stream"))
    }

    @Test
    fun `sniffs url extension when declaration absent`() {
        assertEquals(StreamType.DASH, resolveStreamType(null, "http://x/a.mpd"))
        assertEquals(StreamType.HLS, resolveStreamType(null, "http://x/a.m3u8"))
        assertEquals(StreamType.OTHER, resolveStreamType(null, "http://x/a.ts"))
    }

    @Test
    fun `strips query and fragment before sniffing`() {
        assertEquals(StreamType.DASH, resolveStreamType(null, "http://x/a.mpd?token=123"))
        assertEquals(StreamType.HLS, resolveStreamType(null, "http://x/a.m3u8#frag"))
    }

    @Test
    fun `stable id prefers provided key and is playlist-scoped`() {
        assertEquals("2::key", stableChannelId(2, "key", "http://x/a.m3u8"))
        assertEquals("2::http://x/a.m3u8", stableChannelId(2, null, "http://x/a.m3u8"))
        assertEquals("2::http://x/a.m3u8", stableChannelId(2, "  ", "http://x/a.m3u8"))
    }
}
