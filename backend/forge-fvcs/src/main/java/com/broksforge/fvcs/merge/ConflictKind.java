package com.broksforge.fvcs.merge;

/**
 * The specific shape of a conflict.
 *
 * <ul>
 *   <li>{@link #MODIFY_MODIFY} — the same continuant changed to different revisions on both sides.</li>
 *   <li>{@link #MODIFY_REMOVE} — one side modified a continuant, the other removed it.</li>
 *   <li>{@link #ADD_ADD} — both sides added a continuant with the same logical name but different
 *       identity.</li>
 *   <li>{@link #CRISS_CROSS} — the two histories have more than one merge base (recursive strategy is
 *       future work).</li>
 * </ul>
 */
public enum ConflictKind { MODIFY_MODIFY, MODIFY_REMOVE, ADD_ADD, CRISS_CROSS }
