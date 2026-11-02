package com.pleosstay.app.parking.data

import com.pleosstay.app.parking.domain.FeeEstimateStatus
import com.pleosstay.app.parking.domain.FeePolicy
import com.pleosstay.app.parking.domain.OperatingStatus
import com.pleosstay.app.parking.domain.ParkingPlace
import com.pleosstay.app.parking.domain.ParkingPurpose
import com.pleosstay.app.parking.domain.ParkingSearchResult
import com.pleosstay.app.parking.domain.ParkingType
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

interface ParkingRepository {
    suspend fun search(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        plannedMinutes: Int,
        purpose: ParkingPurpose,
        freeOnly: Boolean,
    ): ParkingSearchResult

    suspend fun detail(placeId: String): ParkingPlace
}

private data class ParkingPlaceDto(
    val json: JSONObject,
) {
    fun toDomain(): ParkingPlace {
        val fee = json.optJSONObject("feePolicy") ?: JSONObject()
        return ParkingPlace(
            id = json.getString("id"),
            name = json.getString("name"),
            type = enumValueOrDefault(json.optString("type"), ParkingType.PARKING_LOT),
            latitude = json.getDouble("latitude"),
            longitude = json.getDouble("longitude"),
            address = json.nullableString("address"),
            distanceMeters = json.nullableInt("distanceMeters"),
            allowedUse = json.optJSONArray("allowedUse")?.let { values ->
                buildList { for (index in 0 until values.length()) add(values.getString(index)) }
            }.orEmpty(),
            eligibility = json.optString("eligibility", "UNKNOWN"),
            eligibilityReason = json.nullableString("eligibilityReason"),
            openingHoursText = json.nullableString("openingHoursText"),
            operatingStatus = enumValueOrDefault(json.optString("operatingStatus"), OperatingStatus.UNKNOWN),
            estimatedFee = json.nullableInt("estimatedFee"),
            feeEstimateStatus = enumValueOrDefault(json.optString("feeEstimateStatus"), FeeEstimateStatus.UNKNOWN),
            feePolicy = FeePolicy(
                kind = fee.optString("kind", "UNKNOWN"),
                basicMinutes = fee.nullableInt("basicMinutes"),
                basicFeeKrw = fee.nullableInt("basicFeeKrw"),
                additionalUnitMinutes = fee.nullableInt("additionalUnitMinutes"),
                additionalUnitFeeKrw = fee.nullableInt("additionalUnitFeeKrw"),
                rawText = fee.nullableString("rawText"),
                uncertaintyReason = fee.nullableString("uncertaintyReason"),
            ),
            parkingCategory = json.nullableString("parkingCategory"),
            parkingStructure = json.nullableString("parkingStructure"),
            capacity = json.nullableInt("capacity"),
            restrictions = json.nullableString("restrictions"),
            operatorName = json.nullableString("operatorName"),
            phoneNumber = json.nullableString("phoneNumber"),
            source = json.optString("source", "UNKNOWN"),
            sourceUpdatedAt = json.nullableString("sourceUpdatedAt"),
        )
    }
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: fallback

private fun JSONObject.nullableString(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

private fun JSONObject.nullableInt(name: String): Int? =
    if (isNull(name) || !has(name)) null else optInt(name)

class ParkingHttpException(
    val statusCode: Int,
    val errorCode: String,
    override val message: String,
) : IOException(message)

class HttpParkingRepository(
    baseUrl: String,
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 15_000,
) : ParkingRepository {
    private val baseUrl = baseUrl.trimEnd('/') + "/"

    override suspend fun search(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        plannedMinutes: Int,
        purpose: ParkingPurpose,
        freeOnly: Boolean,
    ): ParkingSearchResult {
        val query = listOf(
            "lat" to latitude.toString(),
            "lng" to longitude.toString(),
            "radiusMeters" to radiusMeters.toString(),
            "plannedMinutes" to plannedMinutes.toString(),
            "purpose" to purpose.name,
            "freeOnly" to freeOnly.toString(),
        ).joinToString("&") { (key, value) -> "$key=${encode(value)}" }
        val body = request("parking/places?$query")
        val array = body.getJSONArray("places")
        return ParkingSearchResult(
            places = buildList { for (index in 0 until array.length()) add(ParkingPlaceDto(array.getJSONObject(index)).toDomain()) },
            total = body.getInt("total"),
            dataFreshness = body.getString("dataFreshness"),
        )
    }

    override suspend fun detail(placeId: String): ParkingPlace =
        ParkingPlaceDto(request("parking/places/${encode(placeId)}")).toDomain()

    private fun request(relativeUrl: String): JSONObject {
        val connection = URI(baseUrl + relativeUrl).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            val body = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (status !in 200..299) {
                throw ParkingHttpException(status, body.optString("code", "HTTP_ERROR"), body.optString("message", "서버 요청 실패"))
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
