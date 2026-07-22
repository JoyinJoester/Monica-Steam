package takagi.ru.monica.webdav

import org.junit.Assert.assertEquals
import org.junit.Test

class WebDavUrlBuilderTest {

    @Test
    fun normalizeServer_defaultsMissingSchemeToHttps() {
        assertEquals(
            "https://example.com",
            WebDavUrlBuilder.normalizeServer("example.com")
        )
    }

    @Test
    fun normalizeServer_keepsExplicitHttpScheme() {
        assertEquals(
            "http://example.com/dav",
            WebDavUrlBuilder.normalizeServer("http://example.com/dav/")
        )
    }
}
