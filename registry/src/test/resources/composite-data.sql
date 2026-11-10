-- SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
-- SPDX-License-Identifier: Apache-2.0

DELETE FROM application_transfer;
DELETE FROM application;
DELETE FROM team_auth_client;
DELETE FROM auth_client;
DELETE FROM team_member;
DELETE FROM team;
DELETE FROM member;

INSERT INTO team(name, name_normalized, type, external_name, description, version, create_user, create_ts, update_user, update_ts)
VALUES
    ('Team-1', 'team1', 'SHARED', 'AppReg_Team-1', 'team #1', 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW()),
    ('Team-2', 'team2', 'SHARED', 'AppReg_Team-2', 'team #2', 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW()),
    ('Team-3', 'team3', 'SHARED', 'AppReg_Team-3', 'team #3', 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW()),
    ('Team-4', 'team4', 'SHARED', 'AppReg_Team-4', 'team #4', 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW()),
    ('Team-5', 'team5', 'SHARED', 'AppReg_Team-5',  'team #5',0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW()),
    ('Personal-OneTester', 'personalonetester', 'PERSONAL', null, null, 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW()),
    ('Personal-TwoTester', 'personaltwotester', 'PERSONAL', null, null, 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW()),
    ('Personal-SMB-jVaCVc5Ln4fITne','personalsmbjvacvc5ln4fitne','PERSONAL', null,null,0, 'DML_SCRIPT', TIMEZONE('utc', NOW()), 'DML_SCRIPT', TIMEZONE('utc', NOW())),
    ('AppRegistry-Orphan-Apps', 'appregistryorphanapps', 'SHARED', 'AppReg_AppRegistry-Orphan-Apps', 'test support team', 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW());

INSERT INTO application(name, team_id, description, compliance_types, priority, version, create_user, create_ts, update_user, update_ts, ai_enabled, data_perimeter)
VALUES
    ('app1', (SELECT id FROM team WHERE name = 'Personal-OneTester'), 'application #1', null, null, 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW(), false, 'UNRESTRICTED'),
    ('app2', (SELECT id FROM team WHERE name = 'Personal-TwoTester'), 'application #2', null, null, 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW(), false, 'UNRESTRICTED'),
    ('app3', (SELECT id FROM team WHERE name = 'Team-1'), 'application #3', null, null, 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW(), false, 'UNRESTRICTED'),
    ('app4', (SELECT id FROM team WHERE name = 'Team-1'), 'application #4', null, 3, 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW(), false, 'UNRESTRICTED'),
    ('app5', (SELECT id FROM team WHERE name = 'Team-2'), 'application #5', null, null, 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW(), false, 'UNRESTRICTED'),
    ('app6', (SELECT id FROM team WHERE name = 'Team-3'), 'application #6', null, null, 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW(), false, 'UNRESTRICTED'),
    ('app7', (SELECT id FROM team WHERE name = 'Team-4'), 'application #7', null, null, 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW(), false, 'UNRESTRICTED'),
    ('app8', (SELECT id FROM team WHERE name = 'Team-1'), 'application #8', ARRAY ['pci'], 5, 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW(), false, 'UNRESTRICTED'),
    ('app9', (SELECT id FROM team WHERE name = 'Team-2'), 'application #9', null, null, 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW(), false, 'UNRESTRICTED'),
    ('app10', (SELECT id FROM team WHERE name = 'Team-4'), 'application #10', null, null, 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW(), false, 'UNRESTRICTED'),
    ('app11', (SELECT id FROM team WHERE name = 'Team-5'), 'application #11', null, null, 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW(), false, 'UNRESTRICTED');

UPDATE application SET information_classification = 'RESTRICTED' WHERE name = 'app8';

INSERT INTO member(external_id, external_alias, email, name, last_name, first_name, version, create_user, create_ts, update_user, update_ts)
VALUES ('00000000-0000-0000-0000-000000000001', 'onetester', 'one.tester@example.com', 'Tester, One', 'Tester', 'One', 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW()),
       ('00000000-0000-0000-0000-000000000002', 'twotester', 'two.tester@example.com', 'Tester, Two', 'Tester', 'Two', 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW()),
       ('11a111aa-aa11-1a11-a111-1aa1aa111aaa', 'unknownuser', 'user.unknown@example.com', 'Unknown, User', 'Unknown', 'User', 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW()),
       ('00000000-0000-0000-0000-000000000006','SMB_jVaCVc5Ln4fITne','cap.test@example.com','Cap-Test' ,'CAP','TEST',0, 'DML_SCRIPT', TIMEZONE('utc', NOW()), 'DML_SCRIPT', TIMEZONE('utc', NOW()));

INSERT INTO team_member(team_id, member_id, role, version, create_user, create_ts, update_user, update_ts)
VALUES ((SELECT id FROM team WHERE name = 'Personal-OneTester'), (SELECT id from member where external_alias = 'onetester'), 'OWNER', 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW()),
       ((SELECT id FROM team WHERE name = 'Personal-TwoTester'), (SELECT id from member where external_alias = 'twotester'), 'OWNER', 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW()),
       ((SELECT id FROM team WHERE name = 'Team-1'), (SELECT id from member where external_alias = 'onetester'), 'OWNER', 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW()),
       ((SELECT id FROM team WHERE name = 'Team-1'), (SELECT id from member where external_alias = 'twotester'), 'MEMBER', 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW()),
       ((SELECT id FROM team WHERE name = 'Team-2'), (SELECT id from member where external_alias = 'twotester'), 'OWNER', 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW()),
       ((SELECT id FROM team WHERE name = 'Team-2'), (SELECT id from member where external_alias = 'onetester'), 'MEMBER', 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW()),
       ((SELECT id FROM team WHERE name = 'Team-3'), (SELECT id from member where external_alias = 'onetester'), 'OWNER', 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW()),
       ((SELECT id FROM team WHERE name = 'Team-3'), (SELECT id from member where external_alias = 'twotester'), 'VIEWER', 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW()),
       ((SELECT id FROM team WHERE name = 'Team-4'), (SELECT id from member where external_alias = 'onetester'), 'OWNER', 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW()),
       ((SELECT id FROM team WHERE name = 'Team-4'), (SELECT id from member where external_alias = 'twotester'), 'VIEWER', 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW()),
       ((SELECT id FROM team WHERE name = 'Team-5'), (SELECT id from member where external_alias = 'onetester'), 'OWNER', 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW()),
       ((SELECT id FROM team WHERE name = 'Team-5'), (SELECT id from member where external_alias = 'twotester'), 'OWNER', 0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW()),
       ((SELECT id FROM team WHERE name = 'Personal-SMB-jVaCVc5Ln4fITne'), (SELECT id from member where external_alias = 'SMB_jVaCVc5Ln4fITne'), 'OWNER', 0, 'DML_SCRIPT', TIMEZONE('utc', NOW()), 'DML_SCRIPT', TIMEZONE('utc', NOW()));

INSERT INTO application_transfer(application_id, source_team_id, receive_team_id, status, comments, version, create_user, create_ts, update_user, update_ts)
VALUES ((SELECT id FROM application WHERE name = 'app4'), (SELECT id FROM team WHERE name = 'Team-1'), (SELECT id FROM team WHERE name = 'Team-5'), 'PENDING', 'please',  0, 'DML_SCRIPT', NOW(), 'DML_SCRIPT', NOW());

INSERT INTO auth_client(client_id, client_version, description, version, create_user, create_ts, update_user, update_ts)
VALUES ('auth-code-flow', 1, 'Sample - auth code flow', 0, 'DML_SCRIPT', TIMEZONE('utc', NOW()), 'DML_SCRIPT', TIMEZONE('utc', NOW())),
       ('auth-code-flow-pkce', 1, 'Sample - auth code flow with PKCE', 0, 'DML_SCRIPT', TIMEZONE('utc', NOW()), 'DML_SCRIPT', TIMEZONE('utc', NOW())),
       ('client-credentials-flow', 1, 'Sample - client credentials flow', 0, 'DML_SCRIPT', TIMEZONE('utc', NOW()), 'DML_SCRIPT', TIMEZONE('utc', NOW())),
       ('client-credentials-flow', 2, 'Sample - client credentials flow v2', 0, 'DML_SCRIPT', TIMEZONE('utc', NOW()), 'DML_SCRIPT', TIMEZONE('utc', NOW()));

INSERT INTO team_auth_client(team_id, auth_client_id, version, create_user, create_ts, update_user, update_ts)
VALUES ((SELECT id FROM team WHERE name = 'Team-1'), (SELECT id from auth_client where client_id = 'auth-code-flow' and client_version = 1), 0, 'DML_SCRIPT', TIMEZONE('utc', NOW()), 'DML_SCRIPT', TIMEZONE('utc', NOW())),
       ((SELECT id FROM team WHERE name = 'Team-2'), (SELECT id from auth_client where client_id = 'auth-code-flow-pkce' and client_version = 1), 0, 'DML_SCRIPT', TIMEZONE('utc', NOW()), 'DML_SCRIPT', TIMEZONE('utc', NOW())),
       ((SELECT id FROM team WHERE name = 'Team-5'), (SELECT id from auth_client where client_id = 'client-credentials-flow' and client_version = 1), 0, 'DML_SCRIPT', TIMEZONE('utc', NOW()), 'DML_SCRIPT', TIMEZONE('utc', NOW())),
       ((SELECT id FROM team WHERE name = 'Team-5'), (SELECT id from auth_client where client_id = 'client-credentials-flow' and client_version = 2), 0, 'DML_SCRIPT', TIMEZONE('utc', NOW()), 'DML_SCRIPT', TIMEZONE('utc', NOW()));
