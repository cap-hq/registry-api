// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import com.cvshealth.cap.registry.auth.model.JwkPublicKey;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JwksRepository {

    private static final String TABLE_NAME = "oauth2_jwks";
    private static final String COLUMN_NAMES = "kid, kty, key_use, alg, n, e, issued_at, expires_at, created_at";

    private static final String FIND_BY_KID_SQL =
            "SELECT " + COLUMN_NAMES + " FROM " + TABLE_NAME + " WHERE kid = ?";
    private static final String FIND_ALL_SQL =
            "SELECT " + COLUMN_NAMES + " FROM " + TABLE_NAME;
    private static final String FIND_ALL_ACTIVE_SQL =
            "SELECT " + COLUMN_NAMES + " FROM " + TABLE_NAME +
            " WHERE expires_at IS NULL OR expires_at > NOW()";
    private static final String INSERT_SQL =
            "INSERT INTO " + TABLE_NAME + " (kid, kty, key_use, alg, n, e, issued_at, expires_at, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String DELETE_BY_KID_SQL =
            "DELETE FROM " + TABLE_NAME + " WHERE kid = ?";

    @Qualifier("authJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;

    public Optional<JwkPublicKey> findByKid(String kid) {
        List<JwkPublicKey> results = jdbcTemplate.query(FIND_BY_KID_SQL, new JwkPublicKeyRowMapper(), kid);
        return results.stream().findFirst();
    }

    public List<JwkPublicKey> findAll() {
        return jdbcTemplate.query(FIND_ALL_SQL, new JwkPublicKeyRowMapper());
    }

    public List<JwkPublicKey> findAllActive() {
        return jdbcTemplate.query(FIND_ALL_ACTIVE_SQL, new JwkPublicKeyRowMapper());
    }

    public void deleteByKid(String kid) {
        jdbcTemplate.update(DELETE_BY_KID_SQL, kid);
    }

    public void save(JwkPublicKey key) {
        jdbcTemplate.update(INSERT_SQL,
                key.getKid(),
                key.getKty(),
                key.getKeyUse(),
                key.getAlg(),
                key.getN(),
                key.getE(),
                key.getIssuedAt() != null ? Timestamp.from(key.getIssuedAt()) : null,
                key.getExpiresAt() != null ? Timestamp.from(key.getExpiresAt()) : null,
                key.getCreatedAt() != null ? Timestamp.from(key.getCreatedAt()) : Timestamp.from(java.time.Instant.now()));
    }

    private static class JwkPublicKeyRowMapper implements RowMapper<JwkPublicKey> {
        @Override
        public JwkPublicKey mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp issuedAt = rs.getTimestamp("issued_at");
            Timestamp expiresAt = rs.getTimestamp("expires_at");
            Timestamp createdAt = rs.getTimestamp("created_at");
            return JwkPublicKey.builder()
                    .kid(rs.getString("kid"))
                    .kty(rs.getString("kty"))
                    .keyUse(rs.getString("key_use"))
                    .alg(rs.getString("alg"))
                    .n(rs.getString("n"))
                    .e(rs.getString("e"))
                    .issuedAt(issuedAt != null ? issuedAt.toInstant() : null)
                    .expiresAt(expiresAt != null ? expiresAt.toInstant() : null)
                    .createdAt(createdAt != null ? createdAt.toInstant() : null)
                    .build();
        }
    }
}
