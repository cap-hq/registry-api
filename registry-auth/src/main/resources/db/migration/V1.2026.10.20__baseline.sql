-- SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
-- SPDX-License-Identifier: Apache-2.0

-- Baseline schema for registry-auth.
-- Consolidates the migration chain up to V1.2026.10.07 (tag: flyway-pre-flatten).
-- Schema only; no data. Partitions of authentication_events and
-- authentication_events_daily are created at runtime by AuthEventPartitionManager.

-- Spring Authorization Server
CREATE TABLE oauth2_registered_client
(
    id                            varchar(100)                            NOT NULL PRIMARY KEY,
    client_id                     varchar(100)                            NOT NULL,
    client_id_issued_at           timestamp     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    client_secret                 varchar(200)  DEFAULT NULL,
    client_secret_expires_at      timestamp,
    client_name                   varchar(200)                            NOT NULL,
    client_authentication_methods varchar(1000)                           NOT NULL,
    authorization_grant_types     varchar(1000)                           NOT NULL,
    redirect_uris                 text          DEFAULT NULL,
    post_logout_redirect_uris     text          DEFAULT NULL,
    scopes                        varchar(1000)                           NOT NULL,
    client_settings               varchar(2000)                           NOT NULL,
    token_settings                varchar(2000)                           NOT NULL,
    last_access_token_issued_at   timestamp
);

CREATE UNIQUE INDEX oauth2_registered_client_ix1 ON oauth2_registered_client (client_id);
CREATE INDEX oauth2_registered_client_ix2 ON oauth2_registered_client (client_name);

CREATE TABLE oauth2_authorization
(
    id                            varchar(100) NOT NULL PRIMARY KEY,
    registered_client_id          varchar(100) NOT NULL,
    principal_name                varchar(200) NOT NULL,
    authorization_grant_type      varchar(100) NOT NULL,
    authorized_scopes             varchar(1000) DEFAULT NULL,
    attributes                    text,
    state                         varchar(500)  DEFAULT NULL,
    authorization_code_value      text,
    authorization_code_issued_at  timestamp,
    authorization_code_expires_at timestamp,
    authorization_code_metadata   text,
    access_token_value            text,
    access_token_issued_at        timestamp,
    access_token_expires_at       timestamp,
    access_token_metadata         text,
    access_token_type             varchar(100)  DEFAULT NULL,
    access_token_scopes           varchar(1000) DEFAULT NULL,
    oidc_id_token_value           text,
    oidc_id_token_issued_at       timestamp,
    oidc_id_token_expires_at      timestamp,
    oidc_id_token_metadata        text,
    refresh_token_value           text,
    refresh_token_issued_at       timestamp,
    refresh_token_expires_at      timestamp,
    refresh_token_metadata        text,
    user_code_value               text,
    user_code_issued_at           timestamp,
    user_code_expires_at          timestamp,
    user_code_metadata            text,
    device_code_value             text,
    device_code_issued_at         timestamp,
    device_code_expires_at        timestamp,
    device_code_metadata          text,
    first_access_token_issued_at  timestamp,
    session_expires_at            timestamp
) WITH (
    autovacuum_vacuum_scale_factor = 0.05,
    autovacuum_analyze_scale_factor = 0.05,
    fillfactor = 85
);

CREATE INDEX idx_oauth2_authorization_refresh_token_value ON oauth2_authorization (refresh_token_value);

CREATE TABLE oauth2_jwks
(
    kid        varchar(255) NOT NULL PRIMARY KEY,
    kty        varchar(10)  NOT NULL,
    key_use    varchar(10),
    alg        varchar(20),
    n          text         NOT NULL,
    e          varchar(20)  NOT NULL,
    issued_at  timestamp    NOT NULL,
    expires_at timestamp,
    created_at timestamp    NOT NULL DEFAULT now()
);

-- Spring Session JDBC
CREATE TABLE spring_session
(
    primary_id            char(36) NOT NULL,
    session_id            char(36) NOT NULL,
    creation_time         bigint   NOT NULL,
    last_access_time      bigint   NOT NULL,
    max_inactive_interval int      NOT NULL,
    expiry_time           bigint   NOT NULL,
    principal_name        varchar(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes
(
    session_primary_id char(36)     NOT NULL,
    attribute_name     varchar(200) NOT NULL,
    attribute_bytes    bytea        NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session (primary_id) ON DELETE CASCADE
);

-- ShedLock
CREATE TABLE shedlock
(
    name       varchar(64)  NOT NULL PRIMARY KEY,
    lock_until timestamp    NOT NULL,
    locked_at  timestamp    NOT NULL,
    locked_by  varchar(255) NOT NULL
);

-- Authentication events (raw), partitioned by day.
-- BIGSERIAL keeps id values unique across all partitions.
CREATE TABLE authentication_events
(
    id               bigserial,
    client_id        varchar(255) NOT NULL,
    client_name      varchar(255),
    grant_type       varchar(100),
    principal_name   varchar(255),
    principal_oid    varchar(255),
    agent            varchar(255),
    authenticated_at timestamp    NOT NULL DEFAULT now(),
    success          boolean      NOT NULL DEFAULT true,
    event_attributes jsonb,
    external_alias   varchar(255),
    PRIMARY KEY (id, authenticated_at)
) PARTITION BY RANGE (authenticated_at);

CREATE INDEX idx_authentication_events_authenticated_at ON authentication_events (authenticated_at);
CREATE INDEX idx_authentication_events_client_id ON authentication_events (client_id, authenticated_at);
CREATE INDEX idx_authentication_events_client_name ON authentication_events (client_name, authenticated_at);
CREATE INDEX idx_authentication_events_principal_name ON authentication_events (principal_name, authenticated_at);
CREATE INDEX idx_authentication_events_principal_oid ON authentication_events (principal_oid, authenticated_at);
CREATE INDEX idx_authentication_events_external_alias ON authentication_events (lower(external_alias), authenticated_at);

-- Authentication events (daily rollup), partitioned by month.
CREATE TABLE authentication_events_daily
(
    event_date           date         NOT NULL,
    client_id            varchar(255) NOT NULL,
    client_name          varchar(255) NOT NULL,
    grant_type           varchar(100) NOT NULL,
    principal_name       varchar(255) NOT NULL,
    principal_oid        varchar(255) NOT NULL,
    agent                varchar(255) NOT NULL,
    success              boolean      NOT NULL,
    event_count          bigint       NOT NULL,
    first_authentication timestamp,
    last_authentication  timestamp,
    event_details        jsonb,
    external_alias       varchar(255) NOT NULL DEFAULT '',
    PRIMARY KEY (event_date, client_id, client_name, grant_type, principal_name, principal_oid, agent, external_alias, success)
) PARTITION BY RANGE (event_date);

CREATE INDEX idx_authentication_events_daily_client_id ON authentication_events_daily (client_id, event_date);
CREATE INDEX idx_authentication_events_daily_client_name ON authentication_events_daily (client_name, event_date);
CREATE INDEX idx_authentication_events_daily_principal_name ON authentication_events_daily (principal_name, event_date);
CREATE INDEX idx_authentication_events_daily_principal_oid ON authentication_events_daily (principal_oid, event_date);
CREATE INDEX idx_authentication_events_daily_external_alias ON authentication_events_daily (lower(external_alias), event_date);
