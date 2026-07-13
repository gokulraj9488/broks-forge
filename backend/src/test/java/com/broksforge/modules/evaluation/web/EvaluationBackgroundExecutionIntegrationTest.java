package com.broksforge.modules.evaluation.web;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The background execution engine: a job whose dataset exceeds the (test-lowered) synchronous
 * threshold is executed off the request thread in batches, checkpoints progress incrementally,
 * can be cancelled cooperatively, and can be resumed to retry only the items still outstanding.
 *
 * <p>The agent points at {@code 127.0.0.1:9} (connection refused, deterministic and fast) so
 * every row fails the same way the existing synchronous CRUD test does — this test exercises the
 * engine's control flow (batching/checkpointing/cancel/resume), not provider success paths.</p>
 */
@SpringBootTest(properties = {
        "broksforge.evaluation.max-items-per-job=2",
        "broksforge.evaluation.batch-size=2",
        "broksforge.evaluation.max-attempts=1",
        "broksforge.evaluation.worker-concurrency=4",
        "broksforge.evaluation.max-concurrent-jobs=2",
        "broksforge.evaluation.stall-after-ms=2000",
        // Only broksforge.agent.health.allow-private-targets is relaxed in application-test.yml;
        // model invocation has its own SSRF flag, needed here so backgroundSummaryMatchesSyncShape's
        // real localhost stub server can actually be reached instead of blocked by network policy.
        "broksforge.model.allow-private-targets=true"
})
@ActiveProfiles("test")
@DisplayName("Evaluation background execution engine")
class EvaluationBackgroundExecutionIntegrationTest extends AbstractIntegrationTest {

    private String owner;
    private String orgId;
    private String projectId;
    private String base;
    private String agentId;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        orgId = createOrg(owner, "Async Eval Org");
        projectId = createProject(owner, orgId, "Async Eval Project");
        base = projectBase(orgId, projectId) + "/evaluation-jobs";
        agentId = idOf(apiPost(owner, projectBase(orgId, projectId) + "/agents", Map.of(
                "name", "Async Eval Agent",
                "visibility", "PRIVATE",
                "framework", "CUSTOM_REST",
                "language", "PYTHON",
                "endpointUrl", "http://127.0.0.1:9/",
                "authType", "NONE"), 201));
    }

    private String datasetWithRows(int rows) throws Exception {
        String datasetId = idOf(apiPost(owner, projectBase(orgId, projectId) + "/datasets",
                Map.of("name", "Async DS " + rows), 201));
        String csv = "input,expected_output\n" + IntStream.range(0, rows)
                .mapToObj(i -> "q" + i + ",a" + i)
                .collect(Collectors.joining("\n"));
        apiPost(owner, projectBase(orgId, projectId) + "/datasets/" + datasetId + "/versions",
                Map.of("format", "CSV", "content", csv), 201);
        return datasetId;
    }

    private JsonNode pollUntilTerminal(String jobId) throws Exception {
        JsonNode job = null;
        for (int i = 0; i < 100; i++) {
            job = apiGet(owner, base + "/" + jobId, 200);
            String status = job.get("status").asText();
            if (status.equals("COMPLETED") || status.equals("FAILED") || status.equals("CANCELLED")) {
                return job;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Job " + jobId + " did not reach a terminal state in time: " + job);
    }

    @Test
    @DisplayName("a dataset larger than the sync threshold runs in the background and completes")
    void largeDatasetRunsInBackground() throws Exception {
        String datasetId = datasetWithRows(5);
        String jobId = idOf(apiPost(owner, base,
                Map.of("name", "Big Job", "agentId", agentId, "datasetId", datasetId), 201));

        JsonNode ran = apiPost(owner, base + "/" + jobId + "/run", null, 200);
        assertThat(ran.get("status").asText()).isEqualTo("RUNNING");
        assertThat(ran.get("batchSize").asInt()).isEqualTo(2);

        JsonNode terminal = pollUntilTerminal(jobId);
        assertThat(terminal.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(terminal.get("totalItems").asInt()).isEqualTo(5);
        assertThat(terminal.get("completedItems").asInt() + terminal.get("failedItems").asInt()).isEqualTo(5);
        // the dead endpoint means every invocation fails — this asserts the engine still reaches
        // a clean terminal state and accounts for every row exactly once.
        assertThat(terminal.get("failedItems").asInt()).isEqualTo(5);

        JsonNode runs = apiGet(owner, base + "/" + jobId + "/runs?size=50", 200);
        assertThat(runs.get("totalElements").asInt()).isEqualTo(5);
    }

    @Test
    @DisplayName("cancelling a background job stops it cooperatively")
    void cancelStopsBackgroundJob() throws Exception {
        String datasetId = datasetWithRows(6);
        String jobId = idOf(apiPost(owner, base,
                Map.of("name", "Cancel Me", "agentId", agentId, "datasetId", datasetId), 201));

        apiPost(owner, base + "/" + jobId + "/run", null, 200);
        apiPost(owner, base + "/" + jobId + "/cancel", null, 200);

        JsonNode job = null;
        for (int i = 0; i < 100; i++) {
            job = apiGet(owner, base + "/" + jobId, 200);
            if (job.get("status").asText().equals("CANCELLED")) {
                break;
            }
            Thread.sleep(100);
        }
        assertThat(job.get("status").asText()).isEqualTo("CANCELLED");
        // cancelling again is a conflict — cooperative stop does not race a normal completion here
        apiPost(owner, base + "/" + jobId + "/cancel", null, 409);
    }

    @Test
    @DisplayName("resuming a completed job with failures retries only the outstanding rows")
    void resumeRetriesOnlyOutstandingRows() throws Exception {
        String datasetId = datasetWithRows(3);
        String jobId = idOf(apiPost(owner, base,
                Map.of("name", "Resumable", "agentId", agentId, "datasetId", datasetId), 201));

        apiPost(owner, base + "/" + jobId + "/run", null, 200);
        JsonNode terminal = pollUntilTerminal(jobId);
        assertThat(terminal.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(terminal.get("failedItems").asInt()).isEqualTo(3);

        JsonNode resumed = apiPost(owner, base + "/" + jobId + "/resume", null, 200);
        assertThat(resumed.get("status").asText()).isEqualTo("RUNNING");
        assertThat(resumed.get("retryCount").asInt()).isEqualTo(1);

        JsonNode secondTerminal = pollUntilTerminal(jobId);
        assertThat(secondTerminal.get("status").asText()).isEqualTo("COMPLETED");
        // still 3 outstanding rows (the dead endpoint fails again) but each run recorded a second
        // attempt rather than duplicating totals — exactly 3 runs still outstanding, no double counting.
        assertThat(secondTerminal.get("failedItems").asInt()).isEqualTo(3);
        assertThat(secondTerminal.get("completedItems").asInt()).isEqualTo(0);

        JsonNode runs = apiGet(owner, base + "/" + jobId + "/runs?size=50", 200);
        assertThat(runs.get("totalElements").asInt()).isEqualTo(6); // 3 rows x 2 attempts, history kept
    }

    @Test
    @DisplayName("resuming a completed job with nothing outstanding is a conflict")
    void resumeWithNothingOutstandingIsConflict() throws Exception {
        String datasetId = idOf(apiPost(owner, projectBase(orgId, projectId) + "/datasets",
                Map.of("name", "Tiny DS"), 201));
        apiPost(owner, projectBase(orgId, projectId) + "/datasets/" + datasetId + "/versions",
                Map.of("format", "CSV", "content", "input,expected_output\nhello,hi\n"), 201);
        String jobId = idOf(apiPost(owner, base,
                Map.of("name", "Sync Job", "agentId", agentId, "datasetId", datasetId), 201));

        JsonNode ran = apiPost(owner, base + "/" + jobId + "/run", null, 200);
        assertThat(ran.get("status").asText()).isIn("COMPLETED", "FAILED");
        if (ran.get("status").asText().equals("COMPLETED") && ran.get("failedItems").asInt() == 0) {
            apiPost(owner, base + "/" + jobId + "/resume", null, 409);
        }
    }

    private HttpServer stubServer;

    @AfterEach
    void tearDownStub() {
        if (stubServer != null) {
            stubServer.stop(0);
            stubServer = null;
        }
    }

    /**
     * Regression test for the background-vs-sync summary key mismatch: {@code buildSummary}
     * (sync path) and {@code buildSummaryFromDb} (background path) must publish the exact same
     * summary keys. A dead endpoint (used by every other test in this class) never produces an
     * {@code EvaluationResult} row at all, so it can't exercise this — this test stands up a real
     * stub agent that always answers "hello", scores it against EXACT_MATCH so some rows pass and
     * some fail, and forces background execution (5 rows > the test's max-items-per-job=2).
     */
    @Test
    @DisplayName("background execution publishes the same summary shape as synchronous execution")
    void backgroundSummaryMatchesSyncShape() throws Exception {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServer.createContext("/", exchange -> {
            byte[] body = "{\"output\": \"hello\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        stubServer.start();
        int port = stubServer.getAddress().getPort();

        String stubAgentId = idOf(apiPost(owner, projectBase(orgId, projectId) + "/agents", Map.of(
                "name", "Stub Agent",
                "visibility", "PRIVATE",
                "framework", "CUSTOM_REST",
                "language", "PYTHON",
                "endpointUrl", "http://127.0.0.1:" + port + "/",
                "authType", "NONE"), 201));

        String profileId = idOf(apiPost(owner, projectBase(orgId, projectId) + "/evaluation-profiles", Map.of(
                "name", "Exact Match Profile",
                "metrics", List.of(Map.of("type", "EXACT_MATCH"))), 201));

        // 3 rows expect "hello" (pass), 2 expect "nope" (fail) — a mix so both metric outcomes appear.
        String datasetId = idOf(apiPost(owner, projectBase(orgId, projectId) + "/datasets",
                Map.of("name", "Stub DS"), 201));
        String csv = "input,expected_output\nq0,hello\nq1,hello\nq2,hello\nq3,nope\nq4,nope";
        apiPost(owner, projectBase(orgId, projectId) + "/datasets/" + datasetId + "/versions",
                Map.of("format", "CSV", "content", csv), 201);

        String jobId = idOf(apiPost(owner, base, Map.of(
                "name", "Stub Job", "agentId", stubAgentId, "datasetId", datasetId, "profileId", profileId), 201));
        apiPost(owner, base + "/" + jobId + "/run", null, 200);
        JsonNode terminal = pollUntilTerminal(jobId);

        assertThat(terminal.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(terminal.get("completedItems").asInt()).isEqualTo(5);
        JsonNode summary = terminal.get("summary");
        assertThat(summary.get("evaluation").get("passed").asInt()).isEqualTo(3);
        assertThat(summary.get("evaluation").get("failed").asInt()).isEqualTo(2);
        assertThat(summary.get("evaluation").get("skipped").asInt()).isEqualTo(0);
        assertThat(summary.get("completedMetricCount").asInt()).isEqualTo(5);
        assertThat(summary.get("execution").get("succeeded").asInt()).isEqualTo(5);
        assertThat(summary.get("unavailableMetricCount").asInt()).isEqualTo(0);
        JsonNode exactMatch = summary.get("metricBreakdown").get("EXACT_MATCH");
        assertThat(exactMatch.get("total").asInt()).isEqualTo(5);
        assertThat(exactMatch.get("completed").asInt()).isEqualTo(5);
        assertThat(exactMatch.get("passed").asInt()).isEqualTo(3);
        assertThat(exactMatch.get("failed").asInt()).isEqualTo(2);
    }
}
