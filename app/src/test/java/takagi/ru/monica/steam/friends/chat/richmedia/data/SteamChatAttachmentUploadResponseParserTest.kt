package takagi.ru.monica.steam.friends.chat.richmedia.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatAttachmentUploadResponseParserTest {
    private val parser = SteamChatAttachmentUploadResponseParser()

    @Test
    fun parsesOfficialBeginResponseAndKeepsSteamIssuedCloudAuthorization() {
        val response = parser.parseBegin(
            """
            {
              "success": 1,
              "timestamp": 1720000000,
              "hmac": "signed-hmac",
              "result": {
                "use_https": true,
                "url_host": "steamusercontent.com",
                "url_path": "/ugc/chat-image.png?token=1",
                "ugcid": "123456789",
                "request_headers": [
                  {"name": "x-amz-acl", "value": "private"},
                  {"name": "Cookie", "value": "steam-issued-cloud-cookie"},
                  {"name": "Authorization", "value": "signed-cloud-request"},
                  {"name": "Content-Length", "value": "100"}
                ]
              }
            }
            """.trimIndent()
        )

        assertEquals(
            "https://steamusercontent.com/ugc/chat-image.png?token=1",
            response.cloudUrl
        )
        assertEquals(
            listOf(
                "x-amz-acl" to "private",
                "Cookie" to "steam-issued-cloud-cookie",
                "Authorization" to "signed-cloud-request"
            ),
            response.requestHeaders
        )
        assertEquals("123456789", response.ugcId)
        assertEquals(1720000000L, response.timestamp)
        assertEquals("signed-hmac", response.hmac)
    }

    @Test
    fun rejectsSteamBeginBusinessFailureAndInsecureCloudUrl() {
        val rejected = assertThrows(SteamChatUploadException::class.java) {
            parser.parseBegin("""{"success":15,"message":"Session expired"}""")
        }
        assertEquals("Session expired", rejected.message)
        assertTrue(rejected.isAuthenticationFailure)

        val limited = assertThrows(SteamChatUploadException::class.java) {
            parser.parseBegin(
                """{"success":112,"message":"Limited users cannot upload images."}"""
            )
        }
        assertEquals(SteamChatUploadFailure.LIMITED_ACCOUNT, limited.failure)

        val insecure = assertThrows(SteamChatUploadException::class.java) {
            parser.parseBegin(
                """
                {
                  "success": 1,
                  "timestamp": 1720000000,
                  "hmac": "hmac",
                  "result": {
                    "use_https": false,
                    "url_host": "steamusercontent.com",
                    "url_path": "/image.png",
                    "ugcid": "1",
                    "request_headers": []
                  }
                }
                """.trimIndent()
            )
        }
        assertTrue(insecure.message.orEmpty().contains("insecure"))
    }

    @Test
    fun parsesCommitOnlyAfterTopLevelAndNestedSteamSuccess() {
        val response = parser.parseCommit(
            """
            {
              "success": 1,
              "result": {
                "success": true,
                "details": {
                  "url": "https://steamusercontent.com/ugc/final.png"
                }
              }
            }
            """.trimIndent()
        )

        assertEquals("https://steamusercontent.com/ugc/final.png", response.url)
    }

    @Test
    fun rejectsNestedCommitFailureAndMissingSecureMediaUrl() {
        val nestedFailure = assertThrows(SteamChatUploadException::class.java) {
            parser.parseCommit(
                """
                {
                  "success": 1,
                  "result": {"success": 2, "message": "Friend cannot receive files"}
                }
                """.trimIndent()
            )
        }
        assertEquals("Friend cannot receive files", nestedFailure.message)

        val insecureUrl = assertThrows(SteamChatUploadException::class.java) {
            parser.parseCommit(
                """
                {
                  "success": 1,
                  "result": {
                    "success": 1,
                    "details": {"url": "http://steamusercontent.com/ugc/final.png"}
                  }
                }
                """.trimIndent()
            )
        }
        assertTrue(insecureUrl.message.orEmpty().contains("invalid attachment URL"))
    }
    @Test
    fun serviceUnavailableAndSessionConflictAreNotFileRejections() {
        val unavailable = assertThrows(SteamChatUploadException::class.java) {
            parser.parseBegin("""{"success":20,"message":"Service unavailable"}""")
        }
        assertEquals(SteamChatUploadFailure.SERVICE, unavailable.failure)
        org.junit.Assert.assertFalse(unavailable.isAuthenticationFailure)
        assertEquals("Service unavailable", unavailable.message)
        assertEquals(SteamChatUploadFailure.SERVICE, parser.parseFailure("""{"success":20}""")?.failure)

        val sessionConflict = assertThrows(SteamChatUploadException::class.java) {
            parser.parseCommit("""{"success":1,"result":{"success":50,"message":"Logged in elsewhere"}}""")
        }
        assertEquals(SteamChatUploadFailure.AUTHENTICATION, sessionConflict.failure)
        assertTrue(sessionConflict.isAuthenticationFailure)
        assertEquals("Logged in elsewhere", sessionConflict.message)
        assertEquals(SteamChatUploadFailure.AUTHENTICATION, parser.parseFailure("""{"success":50}""")?.failure)
    }

}
