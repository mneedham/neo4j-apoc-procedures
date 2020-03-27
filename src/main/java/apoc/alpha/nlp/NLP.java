package apoc.alpha.nlp;

import apoc.convert.Json;
import apoc.graph.document.builder.DocumentToGraph;
import apoc.graph.util.GraphsConfig;
import apoc.result.MapResult;
import apoc.result.VirtualGraph;
import apoc.result.VirtualNode;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static apoc.load.LoadJson.loadJsonStream;

public class NLP {
    // https://cloud.google.com/docs/authentication/api-keys

    @Context
    public Transaction tx;

    @Procedure
    @Description("apoc.alpha.nlp.stream(config) YIELD value - load from JSON URL (e.g. web-api) while sending headers / payload to import JSON as stream of values if the JSON was an array or a single value if it was a map")
    public Stream<MapResult> stream(@Name(value = "config",defaultValue = "{}") Map<String, Object> config) throws URISyntaxException {
        verifyMandatoryConfig(config, "url", "text", "apiKey");

        Map<String,Object> headers = new HashMap<>();
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

    @Procedure
    @Description("apoc.alpha.nlp.graph(config) YIELD value - load from JSON URL (e.g. web-api) while sending headers / payload to import JSON as stream of values if the JSON was an array or a single value if it was a map")
    public Stream<VirtualGraph> graph(@Name(value = "config",defaultValue = "{}") Map<String, Object> config) throws URISyntaxException {
        verifyMandatoryConfig(config, "url", "text", "apiKey", "node");

        Node node = (Node) config.get("node");
        List<String> propertyKeys = StreamSupport.stream(node.getPropertyKeys().spliterator(), false).collect(Collectors.toList());

        Map<String, Object> graphConfig = new HashMap<>();
        graphConfig.put("write", "false");
        Map<String, Object> mappings = new HashMap<>();
        graphConfig.put("mappings", mappings);

        DocumentToGraph documentToGraph = new DocumentToGraph(tx, new GraphsConfig(graphConfig));
        return stream(config).map(document -> {
            return documentToGraph.create(document.value, new VirtualNode(node, propertyKeys), "HAS_ENTITY");
        });

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
