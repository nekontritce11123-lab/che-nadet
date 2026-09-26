package app.chenadet.data

import app.chenadet.core.OpenMeteo
import app.chenadet.core.Place
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class ApiException(val status: Int) : IOException("Weather service HTTP $status")
class InvalidWeatherData(cause: Exception) : IOException("Weather service returned unusable data", cause)
fun interface HttpTransport { suspend fun get(url: String): String }

class WeatherApi(private val http: HttpTransport = UrlConnectionTransport()) {
    suspend fun weather(place: Place): String = http.get(OpenMeteo.weatherUrl(place))
    suspend fun cities(query: String): String = http.get(OpenMeteo.cityUrl(query))
}

class UrlConnectionTransport : HttpTransport {
    override suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val target = URL(url)
        require(target.protocol == "https" && target.host in setOf("api.open-meteo.com", "geocoding-api.open-meteo.com"))
        currentCoroutineContext().ensureActive()
        val connection = target.openConnection() as HttpsURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 10_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "CheNadet/0.1 (Android; non-commercial client)")
        try {
            if (connection.responseCode != 200) throw ApiException(connection.responseCode)
            if (connection.contentLengthLong > MAX_BYTES) throw IOException("Weather response too large")
            val output = ByteArrayOutputStream()
            connection.inputStream.use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = stream.read(buffer)
                    if (count == -1) break
                    if (output.size() + count > MAX_BYTES) throw IOException("Weather response too large")
                    output.write(buffer, 0, count)
                }
            }
            currentCoroutineContext().ensureActive()
            output.toString("UTF-8")
        } finally { connection.disconnect() }
    }
    private companion object { const val MAX_BYTES = 1_048_576 }
}
