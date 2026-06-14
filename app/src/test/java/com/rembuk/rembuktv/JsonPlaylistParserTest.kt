package com.rembuk.rembuktv

import com.rembuk.rembuktv.data.parser.JsonPlaylistParser
import com.rembuk.rembuktv.domain.model.StreamType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonPlaylistParserTest {

    private val parser = JsonPlaylistParser(
        Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true },
    )

    @Test
    fun `parses well-formed entries`() {
        val json = """
            [
              {"id":"tv1","name":"Channel One","logo":"http://l/1.png","group":"News",
               "url":"http://s/1.mpd","type":"dash"},
              {"id":"tv2","name":"Channel Two","url":"http://s/2.m3u8","type":"hls"}
            ]
        """.trimIndent()

        val channels = parser.parse(json, playlistId = 5)

        assertEquals(2, channels.size)
        assertEquals("Channel One", channels[0].name)
        assertEquals(StreamType.DASH, channels[0].streamType)
        assertEquals("News", channels[0].group)
        assertEquals(5, channels[0].playlistId)
        assertEquals(StreamType.HLS, channels[1].streamType)
    }

    @Test
    fun `tolerates missing optional fields`() {
        val json = """[{"name":"Bare","url":"http://s/bare.ts"}]"""

        val channels = parser.parse(json, playlistId = 1)

        assertEquals(1, channels.size)
        assertNull(channels[0].logoUrl)
        assertNull(channels[0].group)
        assertNull(channels[0].drm)
        assertEquals(StreamType.OTHER, channels[0].streamType)
    }

    @Test
    fun `drops entries without a stream url`() {
        val json = """[{"name":"NoUrl"},{"name":"Ok","url":"http://s/ok.m3u8"}]"""

        val channels = parser.parse(json, playlistId = 1)

        assertEquals(1, channels.size)
        assertEquals("Ok", channels[0].name)
    }

    @Test
    fun `parses drm configuration`() {
        val json = """
            [{"name":"DRM","url":"http://s/d.mpd","type":"dash",
              "drm":{"scheme":"widevine","licenseUrl":"http://lic"}}]
        """.trimIndent()

        val channels = parser.parse(json, playlistId = 1)

        assertEquals("widevine", channels[0].drm?.scheme)
        assertEquals("http://lic", channels[0].drm?.licenseUrl)
    }

    @Test
    fun `derives stable ids scoped to the playlist`() {
        val json = """[{"id":"abc","name":"X","url":"http://s/x.m3u8"}]"""

        val first = parser.parse(json, playlistId = 7).first()
        val second = parser.parse(json, playlistId = 7).first()

        assertEquals(first.id, second.id)
        assertTrue(first.id.startsWith("7::"))
    }

    @Test
    fun `blank input yields empty list`() {
        assertTrue(parser.parse("", playlistId = 1).isEmpty())
    }
}
