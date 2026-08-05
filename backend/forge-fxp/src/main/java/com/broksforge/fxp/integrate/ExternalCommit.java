package com.broksforge.fxp.integrate;

/** An external source-control commit, as seen by a {@link SourceControlAdapter} at the platform edge. */
public record ExternalCommit(String sha, String path, String content, String message, String author) {}
