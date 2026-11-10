-- SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
-- SPDX-License-Identifier: Apache-2.0

-- App.Registry replaces App.AppReg as the REGISTRY scope name (see CustomScopeType).
-- Clients registered before the rename only have App.AppReg, so requesting App.Registry
-- is rejected with invalid_scope. Add App.Registry alongside App.AppReg; App.AppReg stays
-- registered until no client requests it.
UPDATE oauth2_registered_client
SET scopes = scopes || ',App.Registry'
WHERE 'App.AppReg' = ANY (string_to_array(scopes, ','))
  AND NOT 'App.Registry' = ANY (string_to_array(scopes, ','));
