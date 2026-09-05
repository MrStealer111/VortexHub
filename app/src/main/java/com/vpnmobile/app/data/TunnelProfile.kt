package com.vpnmobile.app.data

import org.json.JSONArray
import org.json.JSONObject

data class TunnelProfile(
    val id: String,
    val name: String,
    val flag: String,
    val configText: String,
    val createdAt: Long,
)

fun List<TunnelProfile>.toJson(): String {
    val array = JSONArray()
    forEach { profile ->
        array.put(
            JSONObject()
                .put("id", profile.id)
                .put("name", profile.name)
                .put("flag", profile.flag)
                .put("configText", Crypto.encrypt(profile.configText))
                .put("createdAt", profile.createdAt)
        )
    }
    return array.toString()
}

fun String.toProfiles(): List<TunnelProfile> = try {
    val array = JSONArray(this)
    (0 until array.length()).map { index ->
        val obj = array.getJSONObject(index)
        val raw = obj.getString("configText")
        val dec = try { Crypto.decrypt(raw) } catch (_: Exception) { raw }
        TunnelProfile(
            id = obj.getString("id"),
            name = obj.getString("name"),
            flag = obj.optString("flag", "\uD83C\uDF10"),
            configText = dec,
            createdAt = obj.optLong("createdAt", 0L),
        )
    }
} catch (_: Exception) {
    emptyList()
}
