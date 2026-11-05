package com.pleosstay.app.pickup.domain

enum class PickupSessionStatus { ACTIVE, COMPLETED, CANCELLED, ENDED, EXPIRED }
enum class PickupParticipantStatus { JOINED, DEPARTED, NEARBY, ARRIVED, CANCELLED }
enum class PickupEventType { PARTICIPANT_STATUS, PROXIMITY_CANDIDATE, SESSION_COMPLETED, SESSION_CANCELLED, SESSION_ENDED }

data class PickupParticipant(
    val id: String,
    val displayName: String,
    val status: PickupParticipantStatus,
    val updatedAt: String,
)

data class PickupSession(
    val id: String,
    val waitingSessionId: String?,
    val status: PickupSessionStatus,
    val locationSharingConsent: Boolean,
    val participant: PickupParticipant?,
    val invitationExpiresAt: String?,
    val revision: Int,
    val createdAt: String,
    val endedAt: String?,
)

data class PickupInvitation(
    val invitationId: String,
    val shareUrl: String,
    val expiresAt: String,
)

data class PickupSnapshot(
    val session: PickupSession,
    val proximityCandidate: Boolean,
    val revision: Int,
    val occurredAt: String,
    val vehicleLocationUpdatedAt: String?,
)

data class PickupVehicleLocation(
    val latitude: Double,
    val longitude: Double,
    val observedAt: String,
    val confidence: Float?,
)

data class PickupEvent(
    val id: String,
    val eventType: PickupEventType,
    val revision: Int,
    val occurredAt: String,
    val status: PickupParticipantStatus? = null,
)

data class PickupEventPage(
    val events: List<PickupEvent>,
    val latestRevision: Int,
)
