-- ===========================================================================
-- V30 — Slug uniqueness must exclude soft-deleted rows
-- ---------------------------------------------------------------------------
-- Every slugged aggregate is soft-deletable and enforces its own uniqueness in
-- the service layer with `existsBy...SlugIgnoreCaseAndDeletedFalse` — i.e. a
-- slug is considered free once its owning row is soft-deleted, so the name can
-- be reused. The original schema, however, declared a *plain* UNIQUE constraint
-- over (scope, slug) that counts EVERY row, including soft-deleted ones.
--
-- The two diverge: after soft-deleting an agent (or project, dataset, prompt,
-- ...), the application's own check reports the slug as available and lets the
-- INSERT proceed, but the database still holds the retired row's key and rejects
-- it with `duplicate key value violates unique constraint`. That surfaces to the
-- user as the opaque 409 "The operation violates a data constraint" and makes it
-- impossible to re-register anything by a previously-used name.
--
-- Fix: replace each plain UNIQUE constraint with a PARTIAL unique index scoped to
-- `WHERE deleted = false`. Uniqueness among live rows is preserved exactly (no
-- weakening); retired rows simply stop reserving their slug — which is precisely
-- the semantics the service layer already assumes. Index names are kept identical
-- so existing tooling and the mental model are unchanged.
--
-- Safe on existing data: the previous constraint already guaranteed uniqueness
-- across ALL rows, so it certainly holds across the live subset the partial index
-- covers — the index build cannot fail. No foreign key references these keys
-- (all FKs target the primary key), so dropping the constraints is non-breaking.
-- ===========================================================================

-- Agents — (project_id, slug)
ALTER TABLE agents DROP CONSTRAINT uq_agents_project_slug;
CREATE UNIQUE INDEX uq_agents_project_slug
    ON agents (project_id, slug) WHERE deleted = false;

-- Projects — (organization_id, slug)
ALTER TABLE projects DROP CONSTRAINT uq_projects_org_slug;
CREATE UNIQUE INDEX uq_projects_org_slug
    ON projects (organization_id, slug) WHERE deleted = false;

-- Organizations — (slug)
ALTER TABLE organizations DROP CONSTRAINT uq_organizations_slug;
CREATE UNIQUE INDEX uq_organizations_slug
    ON organizations (slug) WHERE deleted = false;

-- Datasets — (project_id, slug)
ALTER TABLE datasets DROP CONSTRAINT uq_datasets_project_slug;
CREATE UNIQUE INDEX uq_datasets_project_slug
    ON datasets (project_id, slug) WHERE deleted = false;

-- Prompts — (project_id, slug)
ALTER TABLE prompts DROP CONSTRAINT uq_prompts_project_slug;
CREATE UNIQUE INDEX uq_prompts_project_slug
    ON prompts (project_id, slug) WHERE deleted = false;

-- Evaluation profiles — (project_id, slug)
ALTER TABLE evaluation_profiles DROP CONSTRAINT uq_eval_profiles_project_slug;
CREATE UNIQUE INDEX uq_eval_profiles_project_slug
    ON evaluation_profiles (project_id, slug) WHERE deleted = false;
