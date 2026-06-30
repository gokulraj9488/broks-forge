package com.broksforge.modules.prompt.domain;

/**
 * Lifecycle state of a prompt library entry. Archived prompts remain readable and
 * usable by historical evaluation jobs but are hidden from default listings and
 * rejected for new mutations until unarchived.
 */
public enum PromptStatus {
    ACTIVE,
    ARCHIVED
}
