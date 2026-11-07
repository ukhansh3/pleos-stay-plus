package com.pleosstay.app.rest

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import android.util.AtomicFile

internal interface RestPersistence {
    fun presets(): List<RestPreset>
    fun savePresets(values: List<RestPreset>)
    fun session(): RestSession?
    fun saveSession(value: RestSession)
}

internal class RestStore(context: Context) : RestPersistence {
    private val presets = AtomicFile(File(context.noBackupFilesDir, "rest-presets.json"))
    private val session = AtomicFile(File(context.noBackupFilesDir, "rest-session.json"))

    override fun presets(): List<RestPreset> {
        val list = JSONArray(presets.read("[]"))
        return List(list.length()) { list.getJSONObject(it).toPreset() }
    }

    override fun savePresets(values: List<RestPreset>) = presets.write(JSONArray(values.map { it.json() }).toString())

    override fun session(): RestSession? {
        val o = JSONObject(session.read("{}"))
        if (!o.has("preset")) return null
        return RestSession(
            o.getJSONObject("preset").toPreset(), o.getLong("start"), o.getLong("endAt"),
            o.optString("waiting").takeIf { it.isNotEmpty() }, o.getString("token"),
            o.optBoolean("targetReached"), o.optBoolean("targetPrompt"), o.optBoolean("soundPaused"),
            o.optString("reason").takeIf { it.isNotEmpty() }?.let(RestEnd::valueOf),
            o.optLong("targetAlertAt").takeIf { it > 0 },
            RestAlarmMode.valueOf(o.optString("alarmMode", RestAlarmMode.SYSTEM_EXACT.name)),
        )
    }

    override fun saveSession(value: RestSession) = session.write(JSONObject()
        .put("preset", value.preset.json()).put("start", value.startedAt).put("endAt", value.endsAt)
        .put("waiting", value.waitingId ?: "").put("token", value.alarmToken)
        .put("targetReached", value.targetReached).put("targetPrompt", value.targetPrompt)
        .put("soundPaused", value.soundPaused).put("reason", value.end?.name ?: "")
        .put("targetAlertAt", value.targetAlertAt ?: 0L).put("alarmMode", value.alarmMode.name).toString())

    private fun AtomicFile.read(default: String): String =
        if (baseFile.exists() || File(baseFile.path + ".bak").exists()) readFully().toString(Charsets.UTF_8) else default

    private fun AtomicFile.write(value: String) {
        val output = startWrite()
        try {
            output.write(value.toByteArray(Charsets.UTF_8))
            finishWrite(output)
        } catch (error: Exception) {
            failWrite(output)
            throw error
        }
        check(readFully().toString(Charsets.UTF_8) == value) { "휴식 기록을 저장하지 못했습니다." }
    }

    private fun RestPreset.json() = JSONObject().put("id", id).put("name", name).put("minutes", minutes)
        .put("display", display.name).put("sound", sound.name).put("alert", alert.name)
        .put("ev", ev).put("target", target)

    private fun JSONObject.toPreset() = RestPreset(
        getString("id"), getString("name"), getInt("minutes"),
        RestDisplay.valueOf(getString("display")), RestSound.valueOf(getString("sound")),
        RestAlert.valueOf(getString("alert")), getBoolean("ev"), getInt("target"),
    )
}
