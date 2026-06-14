package com.rembuk.rembuktv

import com.rembuk.rembuktv.data.parser.M3uPlaylistParser
import com.rembuk.rembuktv.domain.model.StreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uPlaylistParserTest {

    private val parser = M3uPlaylistParser()

    @Test
    fun `parses extinf attributes and display name`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="one.id" tvg-logo="http://l/1.png" group-title="News",Channel One
            http://s/one.m3u8
            #EXTINF:-1 tvg-logo="http://l/2.png" group-title="Sports",Channel Two
            http://s/two.mpd
        """.trimIndent()

        val channels = parser.parse(m3u, playlistId = 3)

        assertEquals(2, channels.size)
        assertEquals("Channel One", channels[0].name)
        assertEquals("News", channels[0].group)
        assertEquals("http://l/1.png", channels[0].logoUrl)
        assertEquals("one.id", channels[0].tvgId)
        assertEquals(StreamType.HLS, channels[0].streamType)
        assertEquals(StreamType.DASH, channels[1].streamType)
        assertEquals(3, channels[0].playlistId)
    }

    @Test
    fun `drops extinf without a following url`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Dangling
            #EXTINF:-1,Good
            http://s/good.m3u8
        """.trimIndent()

        val channels = parser.parse(m3u, playlistId = 1)

        assertEquals(1, channels.size)
        assertEquals("Good", channels[0].name)
    }

    @Test
    fun `parses kodiprop widevine drm`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Protected
            #KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
            #KODIPROP:inputstream.adaptive.license_key=https://license.example/wv
            http://s/protected.mpd
        """.trimIndent()

        val channels = parser.parse(m3u, playlistId = 1)

        assertEquals(1, channels.size)
        assertEquals("widevine", channels[0].drm?.scheme)
        assertEquals("https://license.example/wv", channels[0].drm?.licenseUrl)
    }

    @Test
    fun `ignores blank lines comments and header`() {
        val m3u = """

            #EXTM3U

            # a random comment
            #EXTINF:-1,Solo
            http://s/solo.m3u8

        """.trimIndent()

        val channels = parser.parse(m3u, playlistId = 1)

        assertEquals(1, channels.size)
        assertEquals("Solo", channels[0].name)
        assertNull(channels[0].drm)
    }

    @Test
    fun `falls back to tvg-name when display name missing`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-name="Fallback Name",
            http://s/x.m3u8
        """.trimIndent()

        val channels = parser.parse(m3u, playlistId = 1)

        assertEquals("Fallback Name", channels[0].name)
    }

    @Test
    fun `keeps display name when a quoted attribute value contains a comma`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="one.id" group-title="News, Sport",Channel One
            http://s/one.m3u8
        """.trimIndent()

        val channels = parser.parse(m3u, playlistId = 1)

        assertEquals(1, channels.size)
        assertEquals("Channel One", channels[0].name)
        assertEquals("News, Sport", channels[0].group)
        assertEquals("one.id", channels[0].tvgId)
    }

    @Test
    fun `does not leak a user-agent fragment into the name (iptv-org index format)`() {
        // Real-world line from https://iptv-org.github.io/iptv/index.m3u: the inline
        // http-user-agent attribute value contains "(KHTML, like Gecko)". Splitting on
        // the first comma overall would make the name "like Gecko) Chrome/...".
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="" tvg-logo="" http-user-agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36 Edg/145.0.0.0" group-title="Undefined",4ACETV (1080p)
            #EXTVLCOPT:http-user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36 Edg/145.0.0.0
            https://example.cloudfront.net/d/x/hls3/m.m3u8
        """.trimIndent()

        val channels = parser.parse(m3u, playlistId = 1)

        assertEquals(1, channels.size)
        assertEquals("4ACETV (1080p)", channels[0].name)
        assertEquals("Undefined", channels[0].group)
        assertEquals(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36 Edg/145.0.0.0",
            channels[0].headers["User-Agent"],
        )
    }

    @Test
    fun `captures http headers declared inline on the extinf line`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 http-user-agent="MyAgent/1.0" http-referrer="https://ref.example/",Inline Headers
            http://s/inline.m3u8
        """.trimIndent()

        val channels = parser.parse(m3u, playlistId = 1)

        assertEquals(1, channels.size)
        assertEquals("Inline Headers", channels[0].name)
        assertEquals("MyAgent/1.0", channels[0].headers["User-Agent"])
        assertEquals("https://ref.example/", channels[0].headers["Referer"])
    }

    @Test
    fun `blank input yields empty list`() {
        assertTrue(parser.parse("   ", playlistId = 1).isEmpty())
    }
}
