package com.broksforge.fvcs.merge;

/**
 * The three levels of merge conflict (Conflict Model §2). Structural conflicts block the merge;
 * semantic and operational findings are warnings on the merge result (the data merges, but promoting or
 * deploying it should not proceed until they are addressed).
 */
public enum ConflictLevel { STRUCTURAL, SEMANTIC, OPERATIONAL }
