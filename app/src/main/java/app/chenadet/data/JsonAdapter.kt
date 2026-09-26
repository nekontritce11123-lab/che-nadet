package app.chenadet.data

import org.json.JSONArray
import org.json.JSONObject

/** Only JSON parsing is Android-specific; normalization is exercised in :core. */
object JsonAdapter {
    fun decode(text: String): Map<String, Any?> = objectMap(JSONObject(text))
    private fun objectMap(obj: JSONObject): Map<String, Any?> = buildMap {
        val keys = obj.keys()
        while (keys.hasNext()) { val key = keys.next(); put(key, value(obj.get(key))) }
    }
    private fun value(raw: Any?): Any? = when (raw) {
        null, JSONObject.NULL -> null
        is JSONObject -> objectMap(raw)
        is JSONArray -> (0 until raw.length()).map { value(raw.get(it)) }
        else -> raw
    }
}
