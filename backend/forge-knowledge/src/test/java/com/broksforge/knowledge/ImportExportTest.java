package com.broksforge.knowledge;

import com.broksforge.knowledge.graph.KnowledgeGraph;
import com.broksforge.knowledge.io.GraphExport;
import com.broksforge.knowledge.io.OntologyExport;
import com.broksforge.knowledge.ontology.Ontologies;
import com.broksforge.knowledge.ontology.Ontology;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ontology and subgraph export produce deterministic, canonical, content-addressable documents. */
class ImportExportTest {

    @Test
    @DisplayName("ontology export is deterministic (byte-identical across runs)")
    void ontologyExportDeterministic() {
        byte[] a = OntologyExport.toBytes(Ontologies.forge());
        byte[] b = OntologyExport.toBytes(Ontologies.forge());
        assertArrayEquals(a, b);
        String json = new String(a, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"objectTypes\""));
        assertTrue(json.contains("\"relationTypes\""));
        assertTrue(json.contains("evaluation-verdict"));
    }

    @Test
    @DisplayName("ontology export contains every registered object type")
    void ontologyExportComplete() {
        Ontology ontology = Ontologies.forge();
        String json = new String(OntologyExport.toBytes(ontology), StandardCharsets.UTF_8);
        for (var type : ontology.objectTypes()) {
            assertTrue(json.contains("\"" + type.subtype() + "\""), "export missing " + type.subtype());
        }
    }

    @Test
    @DisplayName("subgraph export renders objects and relationships deterministically")
    void graphExport() {
        KnowledgeGraph kg = TestSupport.graph();
        KnowledgeGraphTest.build(kg);
        byte[] a = GraphExport.toBytes(kg.view());
        byte[] b = GraphExport.toBytes(kg.view());
        assertArrayEquals(a, b);
        String json = new String(a, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"objects\""));
        assertTrue(json.contains("\"relationships\""));
        assertTrue(json.contains("Deployment"));
    }
}
