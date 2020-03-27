package apoc.nlp;

import apoc.convert.Json;
import apoc.result.MapResult;
import org.apache.http.client.utils.URIBuilder;
import org.neo4j.cypher.internal.runtime.interpreted.commands.QueryString;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static apoc.load.LoadJson.loadJsonStream;

public class NLP {

    @SuppressWarnings("unchecked")
    @Procedure
    @Description("apoc.nlp.stream('url',{header:value},payload, config) YIELD value - load from JSON URL (e.g. web-api) while sending headers / payload to import JSON as stream of values if the JSON was an array or a single value if it was a map")
//    public Stream<MapResult> stream(@Name("urlOrKey") String urlOrKey, @Name("headers") Map<String,Object> headers, @Name("payload") String payload, @Name(value = "path",defaultValue = "") String path, @Name(value = "config",defaultValue = "{}") Map<String, Object> config) {
    public Stream<MapResult> stream(@Name(value = "config",defaultValue = "{}") Map<String, Object> config) throws URISyntaxException {
        verifyMandatoryConfig(config, "url", "text", "apiKey");

        Map<String,Object> headers = new HashMap<>();
//        headers.put("Authorization", "Bearer " + config.get("token"));
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("method", "POST");

        Map<String, Object> payload  = new HashMap<>();
        Map<String, Object> document = new HashMap<>();
        document.put("type", "PLAIN_TEXT");
        document.put("content", config.get("text"));
        payload.put("document", document);

        String url = config.get("url").toString() + "?key=" + config.get("apiKey");

        boolean failOnError = (boolean) config.getOrDefault("failOnError", true);
        return loadJsonStream(url, headers, new Json().toJson(payload), null, failOnError);
    }

    private void verifyMandatoryConfig(@Name(value = "config", defaultValue = "{}") Map<String, Object> config, String... tokens) {
        for (String token : tokens) {
            if (!config.containsKey(token)) {
                throw new IllegalArgumentException(String.format(
                        "No value specified for the mandatory configuration parameter `%s`",
                        token
                ));
            }
        }
    }
}
