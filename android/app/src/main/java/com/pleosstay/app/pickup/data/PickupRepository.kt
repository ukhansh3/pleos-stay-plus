package com.pleosstay.app.pickup.data

import com.pleosstay.app.pickup.auth.DriverIdentityProvider
import com.pleosstay.app.pickup.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.UUID

class PickupApiException(message: String, val statusCode: Int) : Exception(message)

interface PickupRepository {
    suspend fun create(waitingSessionId: String? = null): PickupSession
    suspend fun issueInvitation(sessionId: String): PickupInvitation
    suspend fun sendVehicleLocation(sessionId: String, location: PickupVehicleLocation)
    suspend fun complete(sessionId: String): PickupSession
    suspend fun cancel(sessionId: String, reason: String): PickupSession
    suspend fun endLinkedWaiting(sessionId: String): PickupSession
}

class HttpPickupRepository(
    baseUrl: String,
    private val identity: DriverIdentityProvider,
) : PickupRepository {
    private val apiBase = baseUrl.trimEnd('/')

    override suspend fun create(waitingSessionId: String?): PickupSession {
        val payload = JSONObject().put("locationSharingConsent", true)
        if (waitingSessionId == null) payload.put("waitingSessionId", JSONObject.NULL) else payload.put("waitingSessionId", waitingSessionId)
        return request("POST", "/pickup/sessions", payload, true).toSession()
    }

    override suspend fun issueInvitation(sessionId: String): PickupInvitation {
        val json = request("POST", "/pickup/sessions/$sessionId/invitations", JSONObject(), true)
        return PickupInvitation(json.getString("invitationId"), json.getString("shareUrl"), json.getString("expiresAt"))
    }

    override suspend fun sendVehicleLocation(sessionId: String, location: PickupVehicleLocation) {
        val payload = JSONObject()
            .put("latitude", location.latitude)
            .put("longitude", location.longitude)
            .put("observedAt", location.observedAt)
            .put("source", "REAL")
        location.confidence?.let { payload.put("confidence", it.toDouble()) }
        request("PUT", "/pickup/sessions/$sessionId/vehicle-location", payload, false)
    }

    override suspend fun complete(sessionId: String): PickupSession =
        request("POST", "/pickup/sessions/$sessionId/complete", JSONObject(), true).toSession()

    override suspend fun cancel(sessionId: String, reason: String): PickupSession =
        request("POST", "/pickup/sessions/$sessionId/cancel", JSONObject().put("reason", reason), true).toSession()

    override suspend fun endLinkedWaiting(sessionId: String): PickupSession =
        request("POST", "/pickup/sessions/$sessionId/end", JSONObject(), true).toSession()

    private suspend fun request(method: String, path: String, payload: JSONObject, idempotent: Boolean): JSONObject = withContext(Dispatchers.IO) {
        val session = identity.validSession()
        val connection = URI("$apiBase$path").toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 5_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            connection.setRequestProperty("Content-Type", "application/json")
            if (idempotent) connection.setRequestProperty("Idempotency-Key", UUID.randomUUID().toString())
            connection.outputStream.use { it.write(payload.toString().toByteArray(StandardCharsets.UTF_8)) }
            val status = connection.responseCode
            if (status == 204) return@withContext JSONObject()
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val json = JSONObject(stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty().ifBlank { "{}" })
            if (status !in 200..299) throw PickupApiException(json.optString("message", "Pickup 요청에 실패했습니다 ($status)"), status)
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
