package apoc.create;

import apoc.Description;
import apoc.result.*;
import org.neo4j.graphdb.*;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.PerformsWrites;
import org.neo4j.procedure.Procedure;

import java.util.*;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class Create {

    private static final Label[] NO_LABELS = new Label[0];

    @Context
    public GraphDatabaseService db;

    @Procedure
    @PerformsWrites
    @Description("apoc.create.node(['Label'], {key:value,...}) - create node with dynamic labels")
    public Stream<NodeResult> node(@Name("label") List<String> labelNames, @Name("props") Map<String, Object> props) {
        return Stream.of(new NodeResult(setProperties(db.createNode(labels(labelNames)),props)));
    }

    @Procedure
    @PerformsWrites
    @Description("apoc.create.nodes(['Label'], [{key:value,...}]) create multiple nodes with dynamic labels")
    public Stream<NodeResult> nodes(@Name("label") List<String> labelNames, @Name("props") List<Map<String, Object>> props) {
        Label[] labels = labels(labelNames);
        return props.stream().map(p -> new NodeResult(setProperties(db.createNode(labels), p)));
    }

    @Procedure
    @PerformsWrites
    @Description("apoc.create.relationship(person1,'KNOWS',{key:value,...}, person2) create relationship with dynamic rel-type")
    public Stream<RelationshipResult> relationship(@Name("from") Node from,
                                                   @Name("relType") String relType, @Name("props") Map<String, Object> props,
                                                   @Name("to") Node to) {
        return Stream.of(new RelationshipResult(setProperties(from.createRelationshipTo(to,RelationshipType.withName(relType)),props)));
    }

    @Procedure
    @Description("apoc.create.vNode(['Label'], {key:value,...}) returns a virtual node")
    public Stream<NodeResult> vNode(@Name("label") List<String> labelNames, @Name("props") Map<String, Object> props) {
        Label[] labels = labels(labelNames);
        return Stream.of(new NodeResult(new VirtualNode(labels, props, db)));
    }

    @Procedure
    @Description("apoc.create.vNodes(['Label'], [{key:value,...}]) returns virtual nodes")
    public Stream<NodeResult> vNodes(@Name("label") List<String> labelNames, @Name("props") List<Map<String, Object>> props) {
        Label[] labels = labels(labelNames);
        return props.stream().map(p -> new NodeResult(new VirtualNode(labels, p, db)));
    }

    @Procedure
    @Description("apoc.create.vRelationship(nodeFrom,'KNOWS',{key:value,...}, nodeTo) returns a virtual relationship")
    public Stream<RelationshipResult> vRelationship(@Name("from") Node from, @Name("relType") String relType, @Name("props") Map<String, Object> props, @Name("to") Node to) {
        RelationshipType type = RelationshipType.withName(relType);
        return Stream.of(new RelationshipResult(new VirtualRelationship(from,to,type).withProperties(props)));
    }

    @Procedure
    @Description("apoc.create.vPattern({_labels:['LabelA'],key:value},'KNOWS',{key:value,...}, {_labels:['LabelB'],key:value}) returns a virtual pattern")
    public Stream<VirtualPathResult> vPattern(@Name("from") Map<String,Object> n,
                                              @Name("relType") String relType, @Name("props") Map<String, Object> props,
                                              @Name("to") Map<String,Object> m) {
        n = new LinkedHashMap<>(n); m=new LinkedHashMap<>(m);
        RelationshipType type = RelationshipType.withName(relType);
        VirtualNode from = new VirtualNode(labels(n.remove("_labels")), n, db);
        VirtualNode to = new VirtualNode(labels(m.remove("_labels")), m, db);
        Relationship rel = new VirtualRelationship(from, to, RelationshipType.withName(relType)).withProperties(props);
        return Stream.of(new VirtualPathResult(from, rel, to));
    }

    @Procedure
    @Description("apoc.create.vPatternFull(['LabelA'],{key:value},'KNOWS',{key:value,...},['LabelB'],{key:value}) returns a virtual pattern")
    public Stream<VirtualPathResult> vPatternFull(@Name("labelsN") List<String> labelsN, @Name("n") Map<String,Object> n,
                                                  @Name("relType") String relType, @Name("props") Map<String, Object> props,
                                                  @Name("labelsM") List<String> labelsM, @Name("m") Map<String,Object> m) {
        RelationshipType type = RelationshipType.withName(relType);
        VirtualNode from = new VirtualNode(labels(labelsN), n, db);
        VirtualNode to = new VirtualNode(labels(labelsM), m, db);
        Relationship rel = new VirtualRelationship(from, to, type).withProperties(props);
        return Stream.of(new VirtualPathResult(from,rel,to));
    }

    @Description("TODO apoc.create.vGraph([nodes, {_labels:[],... prop:value,...}], [rels,{_from:keyValueFrom,_to:{_label:,_key:,_value:value}, _type:'KNOWS', prop:value,...}],['pk1','Label2:pk2'])")
    public Stream<VirtualPathResult> vGraph() {
        return Stream.empty();
    }

    private <T extends PropertyContainer> T setProperties(T pc, Map<String, Object> p) {
        if (p == null) return pc;
        for (Map.Entry<String, Object> entry : p.entrySet()) pc.setProperty(entry.getKey(), entry.getValue());
        return pc;
    }

    @Procedure
    @Description("apoc.create.uuid yield uuid - creates an UUID")
    public Stream<UUIDResult> uuid() {
        return Stream.of(new UUIDResult());
    }

    @Procedure
    @Description("apoc.create.uuids(count) yield uuid - creates 'count' UUIDs ")
    public Stream<UUIDResult> uuids(@Name("count") long count) {
        return LongStream.range(0,count).mapToObj( (i) -> new UUIDResult());
    }

    public static class UUIDResult {
        public final String uuid;

        public UUIDResult() {
            this.uuid = UUID.randomUUID().toString();
        }
    }

    private Label[] labels(Object labelNames) {
        if (labelNames==null) return NO_LABELS;
        if (labelNames instanceof List) {
            List names = (List) labelNames;
            Label[] labels = new Label[names.size()];
            int i = 0;
            for (Object l : names) {
                if (l==null) continue;
                labels[i++] = Label.label(l.toString());
            }
            if (i <= labels.length) return Arrays.copyOf(labels,i);
            return labels;
        }
        return new Label[]{Label.label(labelNames.toString())};
    }
    private RelationshipType type(Object type) {
        if (type == null) throw new RuntimeException("No relationship-type provided");
        return RelationshipType.withName(type.toString());
    }

}
