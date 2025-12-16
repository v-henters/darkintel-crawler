package com.darkintel.crawler.dynamodb

import com.darkintel.crawler.model.NormalizedDocument
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse
import java.time.Instant

class DynamoDbDocumentRepositoryTest {

    private lateinit var mockClient: DynamoDbClient

    @BeforeEach
    fun setUp() {
        mockClient = mockk(relaxed = false)
        // Mock the object provider so repository picks up mocked client
        mockkObject(DynamoDbClientProvider)
        every { DynamoDbClientProvider.client } returns mockClient
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(DynamoDbClientProvider)
    }

    private fun sampleDoc(): NormalizedDocument = NormalizedDocument(
        sourceId = "source-1",
        title = "Title",
        url = "http://example.com/post-1",
        contentText = "content",
        attachmentUrls = emptyList(),
        rawMeta = emptyMap(),
        collectedAt = Instant.now()
    )

    @Test
    fun `insertIfNew returns true when putItem succeeds and does not call updateItem`() = runTest {
        // Arrange
        every { mockClient.putItem(any<PutItemRequest>()) } returns mockk<PutItemResponse>()
        // Ensure updateItem is not accidentally called
        // If it is, verify below will catch it

        val repository = DynamoDbDocumentRepository(tableName = "test-documents-table")
        val doc = sampleDoc()

        // Act
        val result = repository.insertIfNew(doc)

        // Assert
        assertTrue(result, "Expected insertIfNew to return true for new document")
        verify(exactly = 1) { mockClient.putItem(any<PutItemRequest>()) }
        verify(exactly = 0) { mockClient.updateItem(any<UpdateItemRequest>()) }
    }

    @Test
    fun `insertIfNew returns false and updates last_seen_at when putItem hits ConditionalCheckFailedException`() = runTest {
        // Arrange
        every { mockClient.putItem(any<PutItemRequest>()) } throws ConditionalCheckFailedException.builder()
            .message("already exists").build()
        every { mockClient.updateItem(any<UpdateItemRequest>()) } returns mockk<UpdateItemResponse>()

        val repository = DynamoDbDocumentRepository(tableName = "test-documents-table")
        val doc = sampleDoc()

        // Act
        val result = repository.insertIfNew(doc)

        // Assert
        assertFalse(result, "Expected insertIfNew to return false when document already exists")
        verify(exactly = 1) { mockClient.putItem(any<PutItemRequest>()) }
        verify(exactly = 1) { mockClient.updateItem(any<UpdateItemRequest>()) }
    }
}
