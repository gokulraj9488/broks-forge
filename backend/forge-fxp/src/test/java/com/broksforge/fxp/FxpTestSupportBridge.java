package com.broksforge.fxp;

import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.knowledge.graph.KnowledgeObject;

/**
 * A public bridge exposing {@link FxpTestSupport} to tests in sub-packages (e.g. the workflow demos), since
 * the support class and its scenario are package-private.
 */
public final class FxpTestSupportBridge {

    private FxpTestSupportBridge() {}

    public static ForgeClient freshClient() {
        return FxpTestSupport.client();
    }

    public static CanonicalValue obj(String key, String value) {
        return FxpTestSupport.obj(key, value);
    }

    public static Fixture scenario() {
        return new Fixture(FxpTestSupport.scenario());
    }

    /** A public view of the scenario with accessor methods usable from any package. */
    public static final class Fixture {
        private final FxpTestSupport.Scenario s;

        Fixture(FxpTestSupport.Scenario s) {
            this.s = s;
        }

        public ForgeClient client() { return s.client; }
        public KnowledgeObject provider() { return s.provider; }
        public KnowledgeObject model() { return s.model; }
        public KnowledgeObject prompt() { return s.prompt; }
        public KnowledgeObject agent() { return s.agent; }
        public KnowledgeObject run() { return s.run; }
        public KnowledgeObject verdict() { return s.verdict; }
        public KnowledgeObject benchmark() { return s.benchmark; }
        public KnowledgeObject env() { return s.env; }
        public KnowledgeObject deployment() { return s.deployment; }
        public KnowledgeObject incident() { return s.incident; }
    }
}
