package com.broksforge.fxp;

import com.broksforge.fxp.cli.ForgeCli;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The CLI is deterministic, greppable, and consumes only the conceptual API. */
class CliTest {

    @Test
    @DisplayName("forge explain renders a complete proof tree for the deployment")
    void explain() {
        FxpTestSupport.Scenario s = FxpTestSupport.scenario();
        ForgeCli cli = new ForgeCli(s.client);
        String out = cli.run("explain", s.deployment.node().toString());
        assertTrue(out.contains("complete=true"), out);
        assertTrue(out.contains("asOf"), out);
    }

    @Test
    @DisplayName("forge impact reports a non-empty blast radius for the provider")
    void impact() {
        FxpTestSupport.Scenario s = FxpTestSupport.scenario();
        ForgeCli cli = new ForgeCli(s.client);
        String out = cli.run("impact", s.provider.node().toString());
        assertTrue(out.contains("radius="), out);
        assertTrue(!out.contains("radius=0"), "provider is depended upon");
    }

    @Test
    @DisplayName("forge validate reports a healthy platform")
    void validate() {
        FxpTestSupport.Scenario s = FxpTestSupport.scenario();
        ForgeCli cli = new ForgeCli(s.client);
        assertTrue(cli.run("validate").contains("healthy=true"));
    }

    @Test
    @DisplayName("an unknown command returns usage, never an exception")
    void unknownCommand() {
        FxpTestSupport.Scenario s = FxpTestSupport.scenario();
        ForgeCli cli = new ForgeCli(s.client);
        assertTrue(cli.run("frobnicate").contains("unknown command"));
        assertTrue(cli.run().contains("forge <command>"));
    }
}
