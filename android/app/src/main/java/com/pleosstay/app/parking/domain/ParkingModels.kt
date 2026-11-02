package com.pleosstay.app.parking.domain

enum class ParkingType { PARKING_LOT, ON_STREET_PARKING, PICKUP_ZONE, TIME_LIMITED_ZONE, PUBLIC_REST_AREA }
enum class ParkingPurpose { PICKUP, SHORT_WAIT, LONG_STAY }
enum class FeeEstimateStatus { ESTIMATED, FREE, UNKNOWN }
enum class OperatingStatus { OPEN, CLOSED, UNKNOWN }

data class FeePolicy(
    val kind: String,
    val basicMinutes: Int?,
    val basicFeeKrw: Int?,
    val additionalUnitMinutes: Int?,
    val additionalUnitFeeKrw: Int?,
    val rawText: String?,
    val uncertaintyReason: String?,
)

data class ParkingPlace(
    val id: String,
    val name: String,
    val type: ParkingType,
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val distanceMeters: Int?,
    val eligibility: String,
    val eligibilityReason: String?,
    val openingHoursText: String?,
    val operatingStatus: OperatingStatus,
    val estimatedFee: Int?,
    val feeEstimateStatus: FeeEstimateStatus,
    val feePolicy: FeePolicy,
    val parkingCategory: String?,
    val parkingStructure: String?,
    val capacity: Int?,
    val restrictions: String?,
    val operatorName: String?,
    val phoneNumber: String?,
    val source: String,
    val sourceUpdatedAt: String?,
)

data class ParkingSearchResult(
    val places: List<ParkingPlace>,
    val total: Int,
    val dataFreshness: String,
)
