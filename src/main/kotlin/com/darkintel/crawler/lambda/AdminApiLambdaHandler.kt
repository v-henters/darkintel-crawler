package com.darkintel.crawler.lambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.darkintel.crawler.dynamodb.DynamoDbClientProvider
import com.darkintel.crawler.config.ConfigLoader
import com.darkintel.crawler.config.RuntimeConfigLoader
import com.darkintel.crawler.dynamodb.DynamoDbSourceScheduleRepository
import com.darkintel.crawler.lambda.dto.RunNowRequest
import com.darkintel.crawler.lambda.dto.ScheduleUpdateRequest
import com.darkintel.crawler.model.SourceScheduleConfig
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.lambda.LambdaClient
import software.amazon.awssdk.services.lambda.model.InvocationType
import software.amazon.awssdk.services.lambda.model.InvokeRequest

class AdminApiLambdaHandler :
    RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private val logger = LoggerFactory.getLogger(AdminApiLambdaHandler::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override fun handleRequest(
        request: APIGatewayProxyRequestEvent?,
        context: Context?
    ): APIGatewayProxyResponseEvent {
        if (request == null) return response(400, mapOf("error" to "bad_request"))

        val method = request.httpMethod ?: ""
        val path = request.path ?: ""

        return try {
            when {
                method.equals("POST", ignoreCase = true) && path.equals("/admin/crawl/run-now", ignoreCase = true) ->
                    handleRunNow(request)

                method.equals("POST", ignoreCase = true) && path.equals("/admin/schedule", ignoreCase = true) ->
                    handleScheduleUpdate(request)

                else -> response(404, mapOf("error" to "not_found"))
            }
        } catch (e: Exception) {
            logger.error("Admin API error: {}", e.message, e)
            response(500, mapOf("error" to "internal_error"))
        }
    }

    private fun handleRunNow(req: APIGatewayProxyRequestEvent): APIGatewayProxyResponseEvent {
        val sourceIdFromQuery = req.queryStringParameters?.get("sourceId")
        val sourceId: String? = sourceIdFromQuery ?: run {
            val body = req.body
            if (!body.isNullOrBlank()) {
                try {
                    json.decodeFromString<RunNowRequest>(body).sourceId
                } catch (_: SerializationException) {
                    null
                }
            } else null
        }

        if (sourceId.isNullOrBlank()) {
            return response(400, mapOf("error" to "missing_sourceId"))
        }

        val functionName = System.getenv("DARKWEB_CRAWLER_LAMBDA_NAME")
        if (functionName.isNullOrBlank()) {
            return response(500, mapOf("error" to "missing_env:DARKWEB_CRAWLER_LAMBDA_NAME"))
        }

        val payloadJson = json.encodeToString(RunNowRequest(sourceId))
        LambdaClient.create().use { client ->
            val invokeReq = InvokeRequest.builder()
                .functionName(functionName)
                .invocationType(InvocationType.EVENT)
                .payload(SdkBytes.fromUtf8String(payloadJson))
                .build()
            client.invoke(invokeReq)
        }

        return response(202, mapOf("status" to "accepted", "sourceId" to sourceId))
    }

    private fun handleScheduleUpdate(req: APIGatewayProxyRequestEvent): APIGatewayProxyResponseEvent {
        val body = req.body
        if (body.isNullOrBlank()) return response(400, mapOf("error" to "missing_body"))

        val dto = try {
            json.decodeFromString<ScheduleUpdateRequest>(body)
        } catch (_: SerializationException) {
            return response(400, mapOf("error" to "invalid_json"))
        }

        if (dto.sourceId.isBlank()) return response(400, mapOf("error" to "invalid_sourceId"))
        if (dto.allowedStartHourUtc != null && (dto.allowedStartHourUtc < 0 || dto.allowedStartHourUtc > 23))
            return response(400, mapOf("error" to "invalid_allowedStartHourUtc"))
        if (dto.allowedEndHourUtc != null && (dto.allowedEndHourUtc < 0 || dto.allowedEndHourUtc > 23))
            return response(400, mapOf("error" to "invalid_allowedEndHourUtc"))

        DynamoDbClientProvider.init(System.getenv("AWS_REGION"))

        // Load application config to get DynamoDB table names
        val runtimeConfig = RuntimeConfigLoader.load(emptyArray())
        val appConfig = ConfigLoader.load(runtimeConfig.configPath)
        val repo = DynamoDbSourceScheduleRepository(
            tableName = appConfig.dynamodb.scheduleTable
        )
        val config = SourceScheduleConfig(
            sourceId = dto.sourceId,
            enabled = dto.enabled,
            allowedStartHourUtc = dto.allowedStartHourUtc,
            allowedEndHourUtc = dto.allowedEndHourUtc
        )
        // Upsert schedule
        runCatching {
            // Repository API is suspend; but handler is sync. Use simple runBlocking? Keep minimal: use kotlinx.coroutines.runBlocking
            kotlinx.coroutines.runBlocking { repo.upsert(config) }
        }.onFailure { e ->
            logger.error("Failed to upsert schedule for {}: {}", dto.sourceId, e.message, e)
            return response(500, mapOf("error" to "dynamo_error"))
        }

        val responseObj = mapOf(
            "status" to "ok",
            "sourceId" to dto.sourceId,
            "enabled" to dto.enabled,
            "allowedStartHourUtc" to dto.allowedStartHourUtc,
            "allowedEndHourUtc" to dto.allowedEndHourUtc
        )
        return response(200, responseObj)
    }

    private fun response(status: Int, obj: Any): APIGatewayProxyResponseEvent {
        val body = try {
            when (obj) {
                is String -> obj
                is Map<*, *> -> json.encodeToString(mapToJson(obj as Map<String, Any?>))
                else -> json.encodeToString(obj)
            }
        } catch (_: Exception) {
            // Fallback simple map -> string
            obj.toString()
        }
        return APIGatewayProxyResponseEvent()
            .withStatusCode(status)
            .withHeaders(mapOf("Content-Type" to "application/json"))
            .withBody(body)
    }

    private fun mapToJson(map: Map<String, Any?>): JsonObject {
        fun anyToJson(value: Any?): JsonElement = when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> mapToJson(value as Map<String, Any?>)
            else -> JsonPrimitive(value.toString())
        }
        return buildJsonObject {
            for ((k, v) in map) {
                if (k != null) put(k, anyToJson(v))
            }
        }
    }
}
