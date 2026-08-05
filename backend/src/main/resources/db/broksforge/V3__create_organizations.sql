-- ===========================================================================
-- V3: Organizations and members
-- ===========================================================================

CREATE TABLE organizations (
    id           UUID          NOT NULL DEFAULT gen_random_uuid(),
    version      BIGINT        NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    created_by   UUID,
    updated_by   UUID,
    deleted      BOOLEAN       NOT NULL DEFAULT FALSE,
    deleted_at   TIMESTAMPTZ,
    deleted_by   UUID,
    name         VARCHAR(120)  NOT NULL,
    slug         VARCHAR(64)   NOT NULL,
    description  VARCHAR(1000),
    status       VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',
    owner_id     UUID          NOT NULL,
    CONSTRAINT pk_organizations PRIMARY KEY (id),
    CONSTRAINT uq_organizations_slug UNIQUE (slug),
    CONSTRAINT fk_organizations_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE INDEX idx_organizations_slug  ON organizations (slug);
CREATE INDEX idx_organizations_owner ON organizations (owner_id);

CREATE TABLE organization_members (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_by       UUID,
    organization_id  UUID         NOT NULL,
    user_id          UUID         NOT NULL,
    role             VARCHAR(32)  NOT NULL DEFAULT 'MEMBER',
    joined_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_organization_members PRIMARY KEY (id),
    CONSTRAINT uq_org_members_org_user UNIQUE (organization_id, user_id),
    CONSTRAINT fk_org_members_org  FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_org_members_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_org_members_org  ON organization_members (organization_id);
CREATE INDEX idx_org_members_user ON organization_members (user_id);
