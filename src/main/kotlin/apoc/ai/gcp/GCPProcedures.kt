package apoc.ai.gcp

import apoc.ai.dto.AIMapResult
import apoc.graph.document.builder.DocumentToGraph
import apoc.graph.util.GraphsConfig
import apoc.result.VirtualGraph
import apoc.result.VirtualNode
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.Relationship
import org.neo4j.graphdb.RelationshipType
import org.neo4j.graphdb.Transaction
import org.neo4j.logging.Log
import org.neo4j.procedure.*
import java.util.stream.Stream

class GCPProcedures {
    @Context
    @JvmField
    var log: Log? = null

    @Context
    @JvmField
    var tx: Transaction? = null

    @Procedure(value = "apoc.ai.gcp.entities.stream", mode = Mode.READ)
    @Description("Returns a stream of entities for provided text")
    fun entitiesStream(@Name("sourceNode") sourceNode: Node,
                       @Name(value = "config", defaultValue = "{}") config: Map<String, Any>)
            : Stream<AIMapResult> {
        verifyKey(config, "key")
        val nodeProperty = getNodeProperty(config)
        verifyNodeProperty(sourceNode, nodeProperty)

        return Stream.of(entities(config, sourceNode, nodeProperty))
    }

    @Procedure(value = "apoc.ai.gcp.entities.graph", mode = Mode.WRITE)
    @Description("Creates a (virtual) entity graph for provided text")
    fun entitiesGraph(@Name("sourceNode") sourceNode: Node,
                      @Name(value = "config", defaultValue = "{}") config: Map<String, Any>)
            : Stream<VirtualGraph> {
        verifyKey(config, "key")
        val nodeProperty = getNodeProperty(config)
        verifyNodeProperty(sourceNode, nodeProperty)

        val response = entities(config, sourceNode, nodeProperty).response

        val storeGraph:Boolean = config.getOrDefault("write", false) as Boolean
        val graphConfig = mapOf(
                "skipValidation" to true,
                "mappings" to mapOf("$" to "Entity{!name,type,@metadata}"),
                "write" to storeGraph
        )

        val documentToGraph = DocumentToGraph(tx, GraphsConfig(graphConfig))
        val graph = documentToGraph.create(response["entities"])

        val mutableGraph = graph.graph.toMutableMap()

        val nodes = (mutableGraph["nodes"] as Set<Node>).toMutableSet()
        val relationships = (mutableGraph["relationships"] as Set<Relationship>).toMutableSet()
        if(storeGraph) {
            createRelationships(sourceNode, nodes, entityRelationshipType(config)).forEach { rel -> relationships.add(rel) }
            nodes.add(sourceNode)
        } else {
            val virtualNode = VirtualNode(sourceNode, sourceNode.propertyKeys.toList())
            createRelationships(virtualNode, nodes, entityRelationshipType(config)).forEach { rel -> relationships.add(rel) }
            nodes.add(virtualNode)
        }

        return Stream.of(VirtualGraph("Graph", nodes, relationships, emptyMap()))
    }

    @Procedure(value = "apoc.ai.gcp.classify.stream", mode = Mode.READ)
    @Description("Classifies a document into categories.")
    fun classifyStream(@Name("sourceNode") sourceNode: Node,
                       @Name(value = "config", defaultValue = "{}") config: Map<String, Any>)
            : Stream<AIMapResult> {
        verifyKey(config, "key")
        val nodeProperty = getNodeProperty(config)
        verifyNodeProperty(sourceNode, nodeProperty)

        return Stream.of(classify(config, sourceNode, nodeProperty))
    }

    @Procedure(value = "apoc.ai.gcp.classify.graph", mode = Mode.WRITE)
    @Description("Classifies a document into categories.")
    fun classifyGraph(@Name("sourceNode") sourceNode: Node,
                      @Name(value = "config", defaultValue = "{}") config: Map<String, Any>)
            : Stream<VirtualGraph> {
        verifyKey(config, "key")
        val nodeProperty = getNodeProperty(config)
        verifyNodeProperty(sourceNode, nodeProperty)

        val response = classify(config, sourceNode, nodeProperty).response

        val storeGraph:Boolean = config.getOrDefault("write", false) as Boolean
        val graphConfig = mapOf(
                "skipValidation" to true,
                "mappings" to mapOf("$" to "Category{!name,type,@metadata}"),
                "write" to storeGraph
        )

        val documentToGraph = DocumentToGraph(tx, GraphsConfig(graphConfig))
        val graph = documentToGraph.create(response["categories"])

        val mutableGraph = graph.graph.toMutableMap()

        val nodes = (mutableGraph["nodes"] as Set<Node>).toMutableSet()
        val relationships = (mutableGraph["relationships"] as Set<Relationship>).toMutableSet()
        if(storeGraph) {
            createRelationships(sourceNode, nodes, classifyRelationshipType(config)).forEach { rel -> relationships.add(rel) }
            nodes.add(sourceNode)
        } else {
            val virtualNode = VirtualNode(sourceNode, sourceNode.propertyKeys.toList())
            createRelationships(virtualNode, nodes, classifyRelationshipType(config)).forEach { rel -> relationships.add(rel) }
            nodes.add(virtualNode)
        }

        return Stream.of(VirtualGraph("Graph", nodes, relationships, emptyMap()))
    }

    @Procedure(value = "apoc.ai.gcp.sentiment.stream", mode = Mode.READ)
    @Description("Analyzes the sentiment of the provided text.")
    fun sentimentStream(@Name("data") data: Any,
                  @Name(value = "config", defaultValue = "{}") config: Map<String, Any>)
            : Stream<AIMapResult> = Stream.of(GCPClient(config["key"].toString(), log!!).sentiment(data, config))

    private fun createRelationships(node: Node, nodes: MutableSet<Node>, relationshipType: RelationshipType) =
            sequence {
                for (n in nodes) {
                    yield(node.createRelationshipTo(n, relationshipType))
                }
            }

    private fun entities(config: Map<String, Any>, node: Node, nodeProperty: String) =
            GCPClient(config["key"].toString(), log!!).entities(node.getProperty(nodeProperty).toString(), config)

    private fun classify(config: Map<String, Any>, node: Node, nodeProperty: String) =
            GCPClient(config["key"].toString(), log!!).classify(node.getProperty(nodeProperty).toString(), config)

    private fun entityRelationshipType(config: Map<String, Any>) =
            RelationshipType.withName(config.getOrDefault("relationshipType", "ENTITY").toString())


    private fun classifyRelationshipType(config: Map<String, Any>) =
            RelationshipType.withName(config.getOrDefault("relationshipType", "CATEGORY").toString())

    private fun getNodeProperty(config: Map<String, Any>): String {
        return config.getOrDefault("nodeProperty", "text").toString()
    }

    private fun verifyNodeProperty(node: Node, nodeProperty: String) {
        if (!node.hasProperty(nodeProperty)) {
            throw IllegalArgumentException("$node does not have property `$nodeProperty`. Property can be configured using parameter `nodeProperty`.")
        }
    }

    private fun verifyKey(config: Map<String, Any>, property: String) {
        if (!config.containsKey(property)) {
            throw IllegalArgumentException("Missing parameter `key`. An API key for the Cloud Natural Language API can be generated from https://console.cloud.google.com/apis/credentials")
        }
    }
}