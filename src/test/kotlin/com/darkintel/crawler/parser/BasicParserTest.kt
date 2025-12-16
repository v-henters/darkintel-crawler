package com.darkintel.crawler.parser

import com.darkintel.crawler.config.SourceConfig
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant
import com.sun.net.httpserver.HttpServer

class BasicParserTest {

    private var server: HttpServer? = null

    private fun startServer(html: String, path: String = "/base"): String {
        val httpServer = HttpServer.create(InetSocketAddress(0), 0)
        httpServer.createContext(path) { exchange ->
            val bytes = html.toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        httpServer.executor = null
        httpServer.start()
        server = httpServer
        val port = httpServer.address.port
        return "http://localhost:$port$path"
    }

    @AfterEach
    fun tearDown() {
        server?.stop(0)
        server = null
    }

    @Test
    fun `basic HTML parsing returns one document with expected fields`() = runBlocking {
        val html = """
            <html>
              <head><title>Leak Post 1</title></head>
              <body>
                <p>This is a leaked credential dump</p>
                <a href="/files/dump.txt">download</a>
              </body>
            </html>
        """.trimIndent()

        val baseUrl = startServer(html)

        val source = SourceConfig(
            id = "source-1",
            name = "Test Source",
            baseUrl = baseUrl,
            parserType = "BASIC",
            crawlIntervalMinutes = 5
        )

        val parser = BasicParser()
        val docs = parser.crawl(source, null)

        assertEquals(1, docs.size)
        val doc = docs.first()
        assertEquals("source-1", doc.sourceId)
        assertEquals("Leak Post 1", doc.title)
        assertTrue(doc.contentText.contains("This is a leaked credential dump"))

        val expectedAttachmentBase = baseUrl.substringBeforeLast("/")
        assertEquals(listOf("$expectedAttachmentBase/files/dump.txt"), doc.attachmentUrls)

        val now = Instant.now()
        assertTrue(Duration.between(doc.collectedAt, now).abs() < Duration.ofMinutes(1))
    }

    @Test
    fun `title falls back to source name when title is blank`() = runBlocking {
        val html = """
            <html>
              <head><title></title></head>
              <body>
                <p>Body without meaningful title</p>
              </body>
            </html>
        """.trimIndent()

        val baseUrl = startServer(html)

        val source = SourceConfig(
            id = "source-2",
            name = "Fallback Name",
            baseUrl = baseUrl,
            parserType = "BASIC",
            crawlIntervalMinutes = 5
        )

        val parser = BasicParser()
        val docs = parser.crawl(source, null)

        assertEquals(1, docs.size)
        val doc = docs.first()
        assertEquals("Fallback Name", doc.title)
    }

    @Test
    fun `no attachment links produces empty attachmentUrls`() = runBlocking {
        val html = """
            <html>
              <head><title>No Attachments</title></head>
              <body>
                <p>Just some text and a regular link</p>
                <a href="/about">About</a>
              </body>
            </html>
        """.trimIndent()

        val baseUrl = startServer(html)

        val source = SourceConfig(
            id = "source-3",
            name = "Test Source",
            baseUrl = baseUrl,
            parserType = "BASIC",
            crawlIntervalMinutes = 5
        )

        val parser = BasicParser()
        val docs = parser.crawl(source, null)

        assertEquals(1, docs.size)
        val doc = docs.first()
        assertTrue(doc.attachmentUrls.isEmpty())
    }

    @Test
    fun `collectedAt is set`() = runBlocking {
        val html = """
            <html>
              <head><title>Collected</title></head>
              <body>Check collected time</body>
            </html>
        """.trimIndent()

        val baseUrl = startServer(html)

        val source = SourceConfig(
            id = "source-4",
            name = "Test Source",
            baseUrl = baseUrl,
            parserType = "BASIC",
            crawlIntervalMinutes = 5
        )

        val parser = BasicParser()
        val docs = parser.crawl(source, null)

        val doc = docs.first()
        val now = Instant.now()
        assertTrue(Duration.between(doc.collectedAt, now).abs() < Duration.ofMinutes(1))
    }
}
