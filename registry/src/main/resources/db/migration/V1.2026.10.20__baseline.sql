-- SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
-- SPDX-License-Identifier: Apache-2.0

-- Baseline schema for the Registry.
--
-- Replaces the incremental migrations V1.2024.01.30 .. V31.2026.10.23 (V1..V29 remain in git
-- history at tag `flyway-pre-flatten`; V30/V31 on main). Schema only: no seed or reference data.
-- Column order, constraint, index and sequence names match the schema those migrations produced.

CREATE EXTENSION IF NOT EXISTS hstore WITH SCHEMA public;

CREATE TABLE public.team (
    id              serial PRIMARY KEY,
    name            character varying(100) NOT NULL UNIQUE,
    name_normalized character varying(100) NOT NULL UNIQUE,
    type            character varying(25)  NOT NULL,
    version         integer                NOT NULL,
    create_user     character varying(50),
    create_ts       timestamp without time zone,
    update_user     character varying(50),
    update_ts       timestamp without time zone,
    description     character varying(500),
    email_list      text,
    slack_channel   text,
    external_name   text
);
CREATE UNIQUE INDEX team_lower_case_name ON public.team (lower((name)::text));
CREATE INDEX team_external_name ON public.team (external_name);

CREATE TABLE public.member (
    id                  serial PRIMARY KEY,
    external_id         character varying(50)  NOT NULL UNIQUE,
    external_alias      character varying(50)  NOT NULL UNIQUE,
    email               character varying(100) NOT NULL,
    name                character varying(100) NOT NULL,
    version             integer                NOT NULL,
    create_user         character varying(50),
    create_ts           timestamp without time zone,
    update_user         character varying(50),
    update_ts           timestamp without time zone,
    last_name           text,
    first_name          text,
    manager_external_id character varying(50),
    employee_id         text
);
CREATE UNIQUE INDEX member_lower_case_email ON public.member (lower((email)::text));
CREATE INDEX member_lower_case_name ON public.member (lower((name)::text));
CREATE INDEX member_manager_external_id ON public.member (manager_external_id);
CREATE INDEX member_upper_external_alias ON public.member (upper((external_alias)::text));

CREATE TABLE public.team_member (
    team_id     integer               NOT NULL REFERENCES public.team (id),
    member_id   integer               NOT NULL REFERENCES public.member (id),
    role        character varying(25) NOT NULL,
    version     integer               NOT NULL,
    create_user character varying(50),
    create_ts   timestamp without time zone,
    update_user character varying(50),
    update_ts   timestamp without time zone,
    PRIMARY KEY (team_id, member_id)
);

CREATE TABLE public.application (
    id             serial PRIMARY KEY,
    team_id        integer                NOT NULL REFERENCES public.team (id),
    name           character varying(100) NOT NULL UNIQUE,
    version        integer                NOT NULL,
    create_user    character varying(50),
    create_ts      timestamp without time zone,
    update_user    character varying(50),
    update_ts      timestamp without time zone,
    description    character varying(500),
    compliance_types text[],
    priority       integer,
    external_id    text,
    cloud          character varying(50),
    tenant         character varying(50),
    ai_enabled     boolean DEFAULT false,
    data_perimeter character varying(20) DEFAULT 'UNRESTRICTED'::character varying NOT NULL,
    information_classification character varying(20)
);
CREATE UNIQUE INDEX application_lower_case_name ON public.application (lower((name)::text));
CREATE INDEX application_cloud ON public.application (cloud);
CREATE INDEX application_tenant ON public.application (tenant);
CREATE INDEX application_upper_external_id ON public.application (upper(external_id));

CREATE TABLE public.application_transfer (
    id              serial PRIMARY KEY,
    application_id  integer                NOT NULL REFERENCES public.application (id),
    source_team_id  integer                NOT NULL REFERENCES public.team (id),
    receive_team_id integer                NOT NULL REFERENCES public.team (id),
    status          character varying(25)  NOT NULL,
    comments        character varying(250),
    version         integer                NOT NULL,
    create_user     character varying(50),
    create_ts       timestamp without time zone,
    update_user     character varying(50),
    update_ts       timestamp without time zone
);

CREATE TABLE public.auth_client (
    id             serial PRIMARY KEY,
    client_id      character varying(50) NOT NULL,
    description    character varying(500),
    version        integer               NOT NULL,
    create_user    character varying(50),
    create_ts      timestamp without time zone,
    update_user    character varying(50),
    update_ts      timestamp without time zone,
    client_version integer DEFAULT 1     NOT NULL
);
CREATE UNIQUE INDEX auth_client_idx1 ON public.auth_client (client_id, client_version);

CREATE TABLE public.team_auth_client (
    team_id        integer NOT NULL REFERENCES public.team (id),
    auth_client_id integer NOT NULL REFERENCES public.auth_client (id),
    version        integer NOT NULL,
    create_user    character varying(50),
    create_ts      timestamp without time zone,
    update_user    character varying(50),
    update_ts      timestamp without time zone,
    PRIMARY KEY (team_id, auth_client_id)
);

CREATE TABLE public.shedlock (
    name       character varying(64)  NOT NULL PRIMARY KEY,
    lock_until timestamp without time zone NOT NULL,
    locked_at  timestamp without time zone NOT NULL,
    locked_by  character varying(255) NOT NULL
);

CREATE TABLE public.audit_log (
    id                 bigserial PRIMARY KEY,
    version            integer NOT NULL,
    entity_type        text    NOT NULL,
    entity_id          integer NOT NULL,
    audit_type         text    NOT NULL,
    changes            jsonb   NOT NULL,
    client_sync_status public.hstore,
    create_user        character varying(50),
    create_ts          timestamp without time zone,
    update_user        character varying(50),
    update_ts          timestamp without time zone
);
CREATE INDEX audit_log_idx1 ON public.audit_log (update_ts);
CREATE INDEX audit_log_idx2 ON public.audit_log (client_sync_status, update_ts);

CREATE TABLE public.line_business (
    id                  serial PRIMARY KEY,
    version             integer NOT NULL,
    name                text    NOT NULL,
    description         text,
    manager_external_id character varying(50),
    manager_name        text,
    create_user         character varying(50),
    create_ts           timestamp without time zone,
    update_user         character varying(50),
    update_ts           timestamp without time zone
);
CREATE UNIQUE INDEX line_business_name_idx ON public.line_business (lower(name));
CREATE INDEX line_business_manager_idx ON public.line_business (manager_external_id);

CREATE TABLE public.line_business_manager (
    id                  serial PRIMARY KEY,
    line_business_id    integer               NOT NULL,
    manager_external_id character varying(50) NOT NULL,
    manager_name        text,
    create_user         character varying(50),
    create_ts           timestamp without time zone,
    CONSTRAINT fk_lob_manager_lob FOREIGN KEY (line_business_id) REFERENCES public.line_business (id) ON DELETE CASCADE
);
CREATE INDEX lob_manager_line_business_idx ON public.line_business_manager (line_business_id);
-- A manager appears at most once per line of business, and in at most one line of business.
CREATE UNIQUE INDEX uq_lob_manager ON public.line_business_manager (line_business_id, lower((manager_external_id)::text));
CREATE UNIQUE INDEX uq_lob_manager_global ON public.line_business_manager (lower((manager_external_id)::text));
