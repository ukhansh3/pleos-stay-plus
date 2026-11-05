package com.pleosstay.app.pickup.data

import com.pleosstay.app.pickup.auth.DriverIdentityProvider
import com.pleosstay.app.pickup.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

class PickupApiException(message: String, val statusCode: Int) : Exception(message)

interface PickupRepository {
    suspend fun create(waitingSessionId: String? = null, idempotencyKey: String): PickupSession
    suspend fun getSession(sessionId: String): PickupSession
    suspend fun events(sessionId: String, afterRevision: Int): PickupEventPage
    suspend fun issueInvitation(sessionId: String, idempotencyKey: String): PickupInvitation
    suspend fun sendVehicleLocation(sessionId: String, location: PickupVehicleLocation)
    suspend fun snapshot(sessionId: String): PickupSnapshot
    suspend fun complete(sessionId: String, idempotencyKey: String): PickupSession
    suspend fun cancel(sessionId: String, reason: String, idempotencyKey: String): PickupSession
    suspend fun endLinkedWaiting(sessionId: String, idempotencyKey: String): PickupSession
}

class HttpPickupRepository(
    baseUrl: String,
    private val identity: DriverIdentityProvider,
) : PickupRepository {
    private val apiBase = baseUrl.trimEnd('/')

    override suspend fun create(waitingSessionId: String?, idempotencyKey: String): PickupSession {
        val payload = JSONObject().put("locationSharingConsent", true)
        if (waitingSessionId == null) payload.put("waitingSessionId", JSONObject.NULL) else payload.put("waitingSessionId", waitingSessionId)
        return request("POST", "/pickup/sessions", payload, idempotencyKey).toSession()
    }

    override suspend fun getSession(sessionId: String): PickupSession =
        request("GET", "/pickup/sessions/$sessionId", JSONObject(), null).toSession()

    override suspend fun events(sessionId: String, afterRevision: Int): PickupEventPage {
        val json = request("GET", "/pickup/sessions/$sessionId/events?afterRevision=$afterRevision", JSONObject(), null)
        val items = json.optJSONArray("events") ?: org.json.JSONArray()
        return PickupEventPage(
            events = (0 until items.length()).map { index ->
                val item = items.getJSONObject(index)
                val payload = item.optJSONObject("payload")
                PickupEvent(
                    id = item.getString("id"),
                    eventType = PickupEventType.valueOf(item.getString("eventType")),
                    revision = item.getInt("revision"),
                    occurredAt = item.getString("occurredAt"),
                    status = payload?.optString("status")?.takeIf(String::isNotBlank)?.let(PickupParticipantStatus::valueOf),
                )
            },
            latestRevision = json.getInt("latestRevision"),
        )
    }

    override suspend fun issueInvitation(sessionId: String, idempotencyKey: String): PickupInvitation {
        val json = request("POST", "/pickup/sessions/$sessionId/invitations", JSONObject(), idempotencyKey)
        return PickupInvitation(json.getString("invitationId"), json.getString("shareUrl"), json.getString("expiresAt"))
    }

    override suspend fun sendVehicleLocation(sessionId: String, location: PickupVehicleLocation) {
        val payload = JSONObject()
            .put("latitude", location.latitude)
            .put("longitude", location.longitude)
            .put("observedAt", location.observedAt)
            .put("source", "REAL")
        location.confidence?.let { payload.put("confidence", it.toDouble()) }
        request("PUT", "/pickup/sessions/$sessionId/vehicle-location", payload, null)
    }

    override suspend fun snapshot(sessionId: String): PickupSnapshot {
        val json = request("GET", "/pickup/sessions/$sessionId/snapshot", JSONObject(), null)
        val vehicle = json.optJSONObject("vehicleLocation")
        return PickupSnapshot(
            session = json.getJSONObject("session").toSession(),
            proximityCandidate = json.getBoolean("proximityCandidate"),
            revision = json.getInt("revision"),
            occurredAt = json.getString("occurredAt"),
            vehicleLocationUpdatedAt = vehicle?.optString("receivedAt")?.ifBlank { null },
        )
    }

    override suspend fun complete(sessionId: String, idempotencyKey: String): PickupSession =
        request("POST", "/pickup/sessions/$sessionId/complete", JSONObject(), idempotencyKey).toSession()

    override suspend fun cancel(sessionId: String, reason: String, idempotencyKey: String): PickupSession =
        request("POST", "/pickup/sessions/$sessionId/cancel", JSONObject().put("reason", reason), idempotencyKey).toSession()

    override suspend fun endLinkedWaiting(sessionId: String, idempotencyKey: String): PickupSession =
        request("POST", "/pickup/sessions/$sessionId/end", JSONObject(), idempotencyKey).toSession()

    private suspend fun request(method: String, path: String, payload: JSONObject, idempotencyKey: String?): JSONObject = withContext(Dispatchers.IO) {
        val session = identity.validSession()
        val connection = URI("$apiBase$path").toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 5_000
            connection.readTimeout = 15_000
            connection.doOutput = method != "GET"
            connection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            connection.setRequestProperty("Content-Type", "application/json")
            idempotencyKey?.let { connection.setRequestProperty("Idempotency-Key", it) }
            if (method != "GET") connection.outputStream.use { it.write(payload.toString().toByteArray(StandardCharsets.UTF_8)) }
            val status = connection.responseCode
            if (status == 204) return@withContext JSONObject()
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val json = JSONObject(stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty().ifBlank { "{}" })
            if (status !in 200..299) throw PickupApiException(json.optJSONObject("error")?.optString("message").orEmpty().ifBlank { json.optString("message", "Pickup 요청에 실패했습니다 ($status)") }, status)
            json
        } finally { connection.disconnect() }
    }
}

private fun JSONObject.toSession(): PickupSession {
    val participantJson = optJSONObject("participant")
    return PickupSession(
        id = getString("id"),
        waitingSessionId = optNullableString("waitingSessionId"),
        status = PickupSessionStatus.valueOf(getString("status")),
        locationSharingConsent = getBoolean("locationSharingConsent"),
        participant = participantJson?.let {
            PickupParticipant(it.getString("id"), it.getString("displayName"), PickupParticipantStatus.valueOf(it.getString("status")), it.getString("updatedAt"))
        },
        invitationExpiresAt = optNullableString("invitationExpiresAt"),
        revision = getInt("revision"),
        createdAt = getString("createdAt"),
        endedAt = optNullableString("endedAt"),
    )
}

private fun JSONObject.optNullableString(name: String): String? = if (!has(name) || isNull(name)) null else getString(name)
