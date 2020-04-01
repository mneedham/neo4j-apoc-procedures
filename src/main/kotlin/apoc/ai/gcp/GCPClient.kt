package apoc.ai.gcp

import apoc.ai.dto.AIMapResult
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.neo4j.logging.Log
import java.io.DataOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection


enum class GCPEndpoint(val method: String) {
    CLASSIFY("/documents:classifyText"),
    SENTIMENT("/documents:analyzeSentiment"),
    ENTITIES("/documents:analyzeEntities")
}

class GCPClient( private val key: String, private val log: Log) {
    private val baseUrl = "https://language.googleapis.com/v1"

    companion object {
        @JvmStatic val MAPPER = jacksonObjectMapper()
    }

    private fun postData(method: String, data: Any, config: Map<String, Any?> = emptyMap()): AIMapResult {
        val fullUrl = "$baseUrl$method?key=$key"
         val url = URL(fullUrl)
        return postData(url, data)
    }

    private fun postData(url: URL, data: Any): AIMapResult {
        val connection = url.openConnection() as HttpsURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true

        connection.setRequestProperty("Content-Type", "application/json")
        DataOutputStream(connection.outputStream).use { it.write(MAPPER.writeValueAsBytes(mapOf("document" to convertInput(data)))) }

        return connection.inputStream
                .use { MAPPER.readValue(it, Any::class.java) }
                .let {result ->
                    AIMapResult((result as Map<String, Any?>))
                }
    }

    private fun convertInput(data: Any): Map<String, Any?> {
        return mapOf("type" to "PLAIN_TEXT", "content" to data)
    }

    fun entities(data: Any, config: Map<String, Any?>): AIMapResult = postData(GCPEndpoint.ENTITIES.method, data)
    fun classify(data: Any, config: Map<String, Any?>): AIMapResult = postData(GCPEndpoint.CLASSIFY.method, data)
    fun sentiment(data: Any, config: Map<String, Any>): AIMapResult = postData(GCPEndpoint.SENTIMENT.method, data)
}