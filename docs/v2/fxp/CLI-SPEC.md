# Forge Experience Platform — CLI Specification

**Deliverable 5.** `forge` is the conceptual API for terminals and CI. It is deterministic and
greppable: every command returns text, every knowledge answer prints the `asOf` log position it was
computed at, and the CLI holds no logic — it formats `ForgeClient` results. Implemented in
[`ForgeCli`](../../../backend/forge-fxp/src/main/java/com/broksforge/fxp/cli/ForgeCli.java).

## Commands

```
forge explain <nodeId>        proof tree of why a node exists / was decided (+ completeness, gaps)
forge provenance <nodeId>     certified derivation history (+ certificate hash)
forge impact <nodeId>         blast radius (radius + dependents)
forge dependencies <nodeId>   reproduction-bearing dependencies (topological)
forge root-cause <nodeId>     causal trace of an incident (+ soundness, anomalies)
forge confidence <nodeId>     min-bound confidence (+ weakest link)
forge evidence <nodeId>       supporting observations/artifacts
forge history <branch>        commit history of a branch
forge search <text>           object search
forge reproduce <nodeId>      reproduce an artifact through the kernel
forge validate                verify the hash chain + integrity scan
forge help                    usage
```

## Design rules

- **Deterministic output.** Every knowledge command prints `(asOf <position>)`; re-running against the
  same log prefix yields identical text. Suitable for `diff`-based CI gates.
- **No exceptions to the user.** A bad argument or unknown command returns usage text and a non-zero
  exit code — never a stack trace.
- **Greppable.** One fact per line, stable prefixes (`- `, `gap: `, `anomaly: `), so `forge explain … |
  grep 'complete=false'` is a valid CI check.
- **Only the conceptual API.** Each command is one `ForgeClient`/experience call plus formatting.

## Example session

```
$ forge validate
validate: chainValid=true  integrityClean=true  errors=0  healthy=true

$ forge explain 8f3c…            # a deployment
explain Deployment  (asOf 27)
  Deployment --rests_on--> EvaluationVerdict
  EvaluationVerdict --cites--> Run
  Deployment --applied--> Agent
  Agent --uses--> Model
  Model --uses--> Provider
  complete=true

$ forge confidence 8f3c…
confidence Deployment: 0.60  weakest=BenchmarkScore  (asOf 27)

$ forge root-cause 1a2b…         # an incident
root-cause Incident  sound=true  (asOf 27)
  - Deployment
```

## CI usage

```
# fail the build if a deployment's decision proof is incomplete
forge explain "$DEPLOY_ID" | grep -q 'complete=true' || exit 1

# fail if platform integrity is compromised
forge validate | grep -q 'healthy=true' || exit 1
```

## Exit codes
`0` success · `1` bad request / usage · `2` platform error (surfaced from the kernel). The message
carries the platform error code verbatim.
