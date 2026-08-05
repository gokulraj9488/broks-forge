package com.broksforge.fxp;

import com.broksforge.fkge.KnowledgeGraphEngine;
import com.broksforge.fxp.copilot.ForgeCopilot;
import com.broksforge.fxp.copilot.LanguageModel;
import com.broksforge.fxp.explore.ExplorerService;
import com.broksforge.fxp.review.ReviewService;
import com.broksforge.fxp.studio.StudioService;
import com.broksforge.fvcs.repo.Repository;
import com.broksforge.kernel.api.ActorId;
import com.broksforge.kernel.api.canonical.CanonicalSerializer;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.reproduce.ReproduceResult;
import com.broksforge.kernel.core.validate.IntegrityScanner;
import com.broksforge.knowledge.graph.KnowledgeObject;

import java.util.List;
import java.util.Locale;

/**
 * The single conceptual API of the Forge Experience Platform. Every surface — CLI, SDKs (Java/Python/TS),
 * and the REST API — mirrors this one API, so the surfaces cannot drift and no hidden logic can accrete.
 *
 * <p>{@code ForgeClient} is a thin orchestration over the platform's public APIs: it exposes the four
 * experiences ({@link #studio()}, {@link #explorer()}, {@link #review()}, {@link #copilot}) and the
 * platform operations ({@link #reproduce}, {@link #validate()}, {@link #search}). It holds no engineering
 * logic and stores nothing.
 */
public final class ForgeClient {

    private final Repository repo;
    private final ActorId actor;
    private final java.util.function.Supplier<KnowledgeGraphEngine> engines;
    private final StudioService studio;
    private final ExplorerService explorer;
    private final ReviewService review;

    private ForgeClient(Repository repo, ActorId actor) {
        this.repo = repo;
        this.actor = actor;
        // The engine is a pure projection of the log; each read re-folds so it reflects the current state.
        // Answers stay deterministic — each carries the LogPosition (asOf) it was computed at.
        this.engines = () -> KnowledgeGraphEngine.open(repo);
        this.studio = new StudioService(repo, engines);
        this.explorer = new ExplorerService(engines);
        this.review = new ReviewService(repo, engines);
    }

    public static ForgeClient open(Repository repo) {
        return new ForgeClient(repo, ActorId.of("forge:fxp"));
    }

    public static ForgeClient open(Repository repo, ActorId actor) {
        return new ForgeClient(repo, actor);
    }

    // ---- Experiences ----

    public StudioService studio() {
        return studio;
    }

    public ExplorerService explorer() {
        return explorer;
    }

    public ReviewService review() {
        return review;
    }

    /** A Copilot bound to a language-model adapter. The adapter only ever narrates FKGE proofs. */
    public ForgeCopilot copilot(LanguageModel model) {
        return new ForgeCopilot(engines, model);
    }

    // ---- Platform operations ----

    /** Reproduce an artifact through the kernel's reproduce operation — the reproducibility guarantee. */
    public ReproduceResult reproduce(KnowledgeObject object) {
        return repo.kernel().reproduce(repo.org(), object.address(), actor);
    }

    /** Validate the org: hash-chain verification + read-side integrity scan — both kernel operations. */
    public PlatformHealth validate() {
        ForgeKernel kernel = repo.kernel();
        boolean chain = kernel.verifyChain(repo.org());
        var report = new IntegrityScanner().scan(kernel, repo.org());
        return new PlatformHealth(chain, report);
    }

    /** Object search over the knowledge read view — a projection, not an authoritative index. */
    public List<KnowledgeObject> search(String text) {
        String needle = text.toLowerCase(Locale.ROOT);
        return repo.knowledge().view().allObjects().stream()
                .filter(o -> matches(o, needle))
                .toList();
    }

    private static boolean matches(KnowledgeObject o, String needle) {
        String hay = (o.type().name() + " " + CanonicalSerializer.toCanonicalString(o.payload()))
                .toLowerCase(Locale.ROOT);
        return hay.contains(needle);
    }

    // ---- Platform accessors (public APIs only) ----

    public Repository repository() {
        return repo;
    }

    public KnowledgeGraphEngine engine() {
        return engines.get();
    }

    public ActorId actor() {
        return actor;
    }
}
