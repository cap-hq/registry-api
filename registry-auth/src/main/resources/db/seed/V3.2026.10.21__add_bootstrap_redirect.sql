-- SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
-- SPDX-License-Identifier: Apache-2.0

-- Add the loopback callback used by bootstrap/bootstrap.sh (oauth2c) to the
-- seeded public PKCE client, so the bootstrap can sign in while the UI keeps
-- http://localhost:3000. Idempotent: skipped when the callback is present.
UPDATE oauth2_registered_client
SET redirect_uris = redirect_uris || ',http://localhost:9876/callback'
WHERE client_id = 'registry-user-pkce'
  AND redirect_uris NOT LIKE '%http://localhost:9876/callback%';
