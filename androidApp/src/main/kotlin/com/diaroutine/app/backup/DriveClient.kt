package com.diaroutine.app.backup

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import kotlin.time.Instant

class DriveException(val code: Int, message: String) : IOException(message)

/**
 * The few Google Drive v3 calls a backup needs, all in the app's hidden appDataFolder.
 * Blocking: call from a background dispatcher.
 */
class DriveClient(private val token: String) {
    data class File(val id: String, val modifiedAt: Instant, val sizeBytes: Long, val keyId: String?)

    /** The backup, if there is one. */
    fun find(): File? {
        val query = URLEncoder.encode("name = '$FILE_NAME' and trashed = false", "UTF-8")
        val url = "$API/files?spaces=appDataFolder&q=$query&orderBy=modifiedTime%20desc&fields=files($FIELDS)"
        val files = JSONObject(request("GET", url).decodeToString()).optJSONArray("files") ?: return null
        return if (files.length() == 0) null else parse(files.getJSONObject(0))
    }

    /** Creates the backup file, or replaces the contents of [existingId]. */
    fun upload(existingId: String?, bytes: ByteArray, keyId: String): File {
        val metadata = JSONObject()
            .put("name", FILE_NAME)
            .put("mimeType", "application/octet-stream")
            .put("appProperties", JSONObject().put("keyId", keyId).put("format", "1"))
        if (existingId == null) metadata.put("parents", JSONArray().put("appDataFolder"))

        val boundary = "diaroutine-" + UUID.randomUUID()
        val body = ByteArrayOutputStream().apply {
            write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n".toByteArray())
            write("--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray())
            write(bytes)
            write("\r\n--$boundary--\r\n".toByteArray())
        }.toByteArray()

        val url = if (existingId == null) {
            "$UPLOAD/files?uploadType=multipart&fields=$FIELDS"
        } else {
            "$UPLOAD/files/$existingId?uploadType=multipart&fields=$FIELDS"
        }
        val response = request(
            method = if (existingId == null) "POST" else "PATCH",
            url = url,
            contentType = "multipart/related; boundary=$boundary",
            body = body,
        )
        return parse(JSONObject(response.decodeToString()))
    }

    fun download(id: String): ByteArray = request("GET", "$API/files/$id?alt=media")

    fun delete(id: String) {
        request("DELETE", "$API/files/$id")
    }

    /** The Google account's email, to show which account backups go to. */
    fun accountEmail(): String? =
        JSONObject(request("GET", "$API/about?fields=user(emailAddress)").decodeToString())
            .optJSONObject("user")?.optString("emailAddress")?.takeIf { it.isNotBlank() }

    private fun parse(json: JSONObject) = File(
        id = json.getString("id"),
        modifiedAt = Instant.parse(json.getString("modifiedTime")),
        sizeBytes = json.optString("size", "0").toLongOrNull() ?: 0,
        keyId = json.optJSONObject("appProperties")?.optString("keyId")?.takeIf { it.isNotBlank() },
    )

    private fun request(method: String, url: String, contentType: String? = null, body: ByteArray? = null): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            // HttpURLConnection has no PATCH; Google APIs accept the override header.
            if (method == "PATCH") {
                connection.requestMethod = "POST"
                connection.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            } else {
                connection.requestMethod = method
            }
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.use { it.readBytes().decodeToString() }.orEmpty()
                throw DriveException(code, "Google Drive answered $code ${detail.take(200)}")
            }
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val FILE_NAME = "diaroutine-backup.bin"
        const val FIELDS = "id,modifiedTime,size,appProperties"
    }
}
