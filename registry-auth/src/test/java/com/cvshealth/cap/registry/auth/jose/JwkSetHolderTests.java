// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.jose;

import com.cvshealth.cap.registry.auth.metric.BigDecimalMetricData;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.cvshealth.cap.registry.auth.jose.JwkSetHolder.METRIC_REGISTRY_COUNTS;
import static com.cvshealth.cap.registry.auth.jose.JwkSetHolder.TYPE_RSA_KEYS;
import static com.cvshealth.cap.registry.auth.jose.JwkSetHolder.TYPE_RSA_KEY_EXPIRATION_DAYS;
import static com.cvshealth.cap.registry.auth.jose.JwkSetHolder.TYPE_RSA_KEY_ISSUED_DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

class JwkSetHolderTests {

    /** Fixed reference instant used across all time-based test helpers. Far-future to be after any key's notBeforeTime. */
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private JwkSetHolder holderWithRandomKey() {
        return new JwkSetHolder(null, false, null, Jwks.DEFAULT_KEY_ID, FIXED_CLOCK);
    }

    private JwkSetHolder holderWithRandomKey(Clock clock) {
        return new JwkSetHolder(null, false, null, Jwks.DEFAULT_KEY_ID, clock);
    }

    @Test
    void initialKey_isLoaded() {
        JwkSetHolder holder = holderWithRandomKey();
        assertThat(holder.getAll().getKeys()).hasSize(1);
    }

    @Test
    void constructor_withCreateLegacyKeyEnabled_certBeforeCutoff_addsLegacyAliasKey() {
        // Jwks.generateRsa() sets notBefore to real system time; cutoff far in the future guarantees notBefore < cutoff
        Instant futureCutoff = Instant.parse("2040-01-01T00:00:00Z");
        JwkSetHolder holder = new JwkSetHolder(null, true, futureCutoff, Jwks.DEFAULT_KEY_ID, FIXED_CLOCK);

        List<com.nimbusds.jose.jwk.JWK> keys = holder.getAll().getKeys();
        assertThat(keys).hasSize(2);
        assertThat(keys).extracting(com.nimbusds.jose.jwk.JWK::getKeyID)
                .contains(Jwks.DEFAULT_KEY_ID);
    }

    @Test
    void constructor_withCreateLegacyKeyEnabled_certAfterCutoff_doesNotAddLegacyAliasKey() {
        // Cutoff far in the past guarantees notBefore > cutoff → no legacy key
        Instant pastCutoff = Instant.parse("2020-01-01T00:00:00Z");
        JwkSetHolder holder = new JwkSetHolder(null, true, pastCutoff, Jwks.DEFAULT_KEY_ID, FIXED_CLOCK);

        assertThat(holder.getAll().getKeys()).hasSize(1);
        assertThat(holder.getAll().getKeys()).extracting(com.nimbusds.jose.jwk.JWK::getKeyID)
                .doesNotContain(Jwks.DEFAULT_KEY_ID);
    }

    @Test
    void constructor_withCreateLegacyKeyDisabled_doesNotAddLegacyAliasKey() {
        Instant futureCutoff = Instant.parse("2040-01-01T00:00:00Z");
        JwkSetHolder holder = new JwkSetHolder(null, false, futureCutoff, Jwks.DEFAULT_KEY_ID, FIXED_CLOCK);

        assertThat(holder.getAll().getKeys()).hasSize(1);
    }

    @Test
    void addKey_prependsNewestFirst() {
        JwkSetHolder holder = holderWithRandomKey();
        RSAKey oldKey = (RSAKey) holder.getAll().getKeys().get(0);

        RSAKey newKey = Jwks.generateRsa();
        holder.addKey(newKey);

        List<?> keys = holder.getAll().getKeys();
        assertThat(keys).hasSize(2);
        assertThat(keys.get(0)).isEqualTo(newKey);
        assertThat(keys.get(1)).isEqualTo(oldKey);
    }

    @Test
    void evictExpired_removesOnlyExpiredKeys() {
        JwkSetHolder holder = holderWithRandomKey();

        // Build a key whose cert expired 13 hours ago — beyond the 12h default grace period
        RSAKey expiredKey = expiredBeyondGraceKey();
        // Insert expired key at back (simulate an old key)
        holder.addKey(expiredKey);
        // Insert a non-expiring fresh key at front
        RSAKey freshKey = Jwks.generateRsa();
        holder.addKey(freshKey);
        assertThat(holder.getAll().getKeys()).hasSize(3);

        List<RSAKey> evicted = holder.evictExpired();

        List<com.nimbusds.jose.jwk.JWK> remaining = holder.getAll().getKeys();
        assertThat(remaining).doesNotContain(expiredKey);
        assertThat(remaining).contains(freshKey);
        assertThat(evicted).containsExactly(expiredKey);
    }

    @Test
    void evictExpired_retainsKey_withinGracePeriod() {
        JwkSetHolder holder = holderWithRandomKey();

        // Key whose cert expired 1 hour ago — still within the 12h default grace period
        RSAKey recentlyExpiredKey = expiredKey();
        holder.addKey(recentlyExpiredKey);

        List<RSAKey> evicted = holder.evictExpired();

        assertThat(evicted).isEmpty();
        assertThat(holder.getAll().getKeys()).contains(recentlyExpiredKey);
    }

    @Test
    void evictExpired_noOp_whenNoExpiredKeys() {
        JwkSetHolder holder = holderWithRandomKey();
        int before = holder.getAll().getKeys().size();

        List<RSAKey> evicted = holder.evictExpired();

        assertThat(holder.getAll().getKeys()).hasSize(before);
        assertThat(evicted).isEmpty();
    }

    @Test
    void getAll_returnsSnapshot_notLiveReference() {
        JwkSetHolder holder = holderWithRandomKey();
        JWKSet snapshot1 = holder.getAll();

        holder.addKey(Jwks.generateRsa());
        JWKSet snapshot2 = holder.getAll();

        assertThat(snapshot1.getKeys()).hasSize(1);
        assertThat(snapshot2.getKeys()).hasSize(2);
    }

    @Test
    void addKey_populatesKeyCountMetricData() {
        JwkSetHolder holder = holderWithRandomKey(); // starts with 1 key

        holder.addKey(Jwks.generateRsa()); // now 2 keys

        Map<String, BigDecimalMetricData> data = getMetricData(holder);
        assertThat(data).isNotNull().hasSize(3);
        BigDecimalMetricData entry = data.get(TYPE_RSA_KEYS);
        assertThat(entry.getValue()).isEqualByComparingTo(BigDecimal.valueOf(2));
        assertThat(entry.getAttributes().get(AttributeKey.stringKey("type"))).isEqualTo(TYPE_RSA_KEYS);
    }

    @Test
    void recordKeyCounts_withKeyHavingNoExpiry_setsExpirationDaysToZero() {
        JwkSetHolder holder = holderWithRandomKey(); // initial random key has no expiry

        holder.recordKeyCounts();

        Map<String, BigDecimalMetricData> data = getMetricData(holder);
        BigDecimalMetricData entry = data.get(TYPE_RSA_KEY_EXPIRATION_DAYS);
        assertThat(entry.getValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(entry.getAttributes().get(AttributeKey.stringKey("type"))).isEqualTo(TYPE_RSA_KEY_EXPIRATION_DAYS);
    }

    @Test
    void addKey_withKeyHavingFutureExpiry_setsExpirationDaysAsFlooredWholeNumber() throws Exception {
        JwkSetHolder holder = holderWithRandomKey();
        // expires 30 days from now
        RSAKey keyWith30DayExpiry = keyWithExpirationTime(30L * 24 * 60 * 60 * 1000, "-expiring");

        holder.addKey(keyWith30DayExpiry);

        Map<String, BigDecimalMetricData> data = getMetricData(holder);
        BigDecimalMetricData entry = data.get(TYPE_RSA_KEY_EXPIRATION_DAYS);
        double expirationDays = entry.getValue().doubleValue();
        // floor-truncated to whole days; slight elapsed time means 29 or 30 are both valid
        assertThat(expirationDays).isGreaterThanOrEqualTo(29.0).isLessThanOrEqualTo(30.0);
        assertThat(entry.getValue().scale()).isZero();
        assertThat(entry.getAttributes().get(AttributeKey.stringKey("type"))).isEqualTo(TYPE_RSA_KEY_EXPIRATION_DAYS);
    }

    @Test
    void addKey_withKeyExpiringInLessThanOneDay_setsExpirationDaysToTwoDecimalPlaces() throws Exception {
        JwkSetHolder holder = holderWithRandomKey();
        // expires 1 hour from now
        RSAKey keyWithOneHourExpiry = keyWithExpirationTime(60L * 60 * 1000, "-expiring-soon");

        holder.addKey(keyWithOneHourExpiry);

        Map<String, BigDecimalMetricData> data = getMetricData(holder);
        BigDecimalMetricData entry = data.get(TYPE_RSA_KEY_EXPIRATION_DAYS);
        double expirationDays = entry.getValue().doubleValue();
        // 1 hour ≈ 0.04 days, rounded to 2 decimal places
        assertThat(expirationDays).isGreaterThanOrEqualTo(0.04).isLessThanOrEqualTo(0.05);
        assertThat(entry.getValue().scale()).isEqualTo(2);
    }

    @Test
    void recordKeyCounts_withKeyHavingNoNotBeforeTime_setsIssuedDaysToZero() {
        JwkSetHolder holder = holderWithRandomKey();
        // Add a key without notBeforeTime at the front (newest) so it's the one used for metrics
        RSAKey keyWithoutNotBefore = keyWithExpirationTime(30L * 24 * 60 * 60 * 1000, "-no-nbf");
        holder.addKey(keyWithoutNotBefore);

        holder.recordKeyCounts();

        Map<String, BigDecimalMetricData> data = getMetricData(holder);
        BigDecimalMetricData entry = data.get(TYPE_RSA_KEY_ISSUED_DAYS);
        assertThat(entry.getValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(entry.getAttributes().get(AttributeKey.stringKey("type"))).isEqualTo(TYPE_RSA_KEY_ISSUED_DAYS);
    }

    @Test
    void addKey_withKeyIssuedBelowWarnThreshold_setsIssuedDaysAsFlooredWholeNumber() throws Exception {
        JwkSetHolder holder = holderWithRandomKey();
        // issued 5 days ago — below 80-day warn threshold
        RSAKey keyIssued5DaysAgo = keyWithIssueTime(5L * 24 * 60 * 60 * 1000, "-issued");

        holder.addKey(keyIssued5DaysAgo);

        Map<String, BigDecimalMetricData> data = getMetricData(holder);
        BigDecimalMetricData entry = data.get(TYPE_RSA_KEY_ISSUED_DAYS);
        double issuedDays = entry.getValue().doubleValue();
        // floor-truncated to whole days; slight elapsed time means 4 or 5 are both valid
        assertThat(issuedDays).isGreaterThanOrEqualTo(4.0).isLessThanOrEqualTo(5.0);
        assertThat(entry.getValue().scale()).isZero();
        assertThat(entry.getAttributes().get(AttributeKey.stringKey("type"))).isEqualTo(TYPE_RSA_KEY_ISSUED_DAYS);
    }

    @Test
    void addKey_withKeyIssuedInTheFuture_returnsNegativeIssuedDays() throws Exception {
        JwkSetHolder holder = holderWithRandomKey();
        // future-dated issuedAt (5 days ahead) — returns negative days
        RSAKey keyIssuedInFuture = keyWithIssueTime(-5L * 24 * 60 * 60 * 1000, "-issued-future");

        holder.addKey(keyIssuedInFuture);

        Map<String, BigDecimalMetricData> data = getMetricData(holder);
        // daysSince returns negative when timestamp is in the future
        BigDecimal issuedDays = data.get(TYPE_RSA_KEY_ISSUED_DAYS).getValue();
        assertThat(issuedDays).isEqualByComparingTo(BigDecimal.valueOf(-5));
        assertThat(issuedDays.signum()).isNegative();
    }

    @Test
    void addKey_withKeyIssuedOneHourInTheFuture_returnsNegativeSubDayValue() throws Exception {
        JwkSetHolder holder = holderWithRandomKey();
        // future-dated issuedAt (1 hour ahead) — returns negative sub-day value
        RSAKey keyIssuedSlightlyInFuture = keyWithIssueTime(-60L * 60 * 1000, "-issued-future-1h");

        holder.addKey(keyIssuedSlightlyInFuture);

        Map<String, BigDecimalMetricData> data = getMetricData(holder);
        BigDecimal issuedDays = data.get(TYPE_RSA_KEY_ISSUED_DAYS).getValue();
        // 1 hour in future → -0.04 (negated sub-day formatting)
        assertThat(issuedDays).isEqualByComparingTo(new BigDecimal("-0.04"));
        assertThat(issuedDays.scale()).isEqualTo(2);
    }

    @Test
    void evictExpired_populatesMetricData_whenKeysRemoved() {
        JwkSetHolder holder = holderWithRandomKey();
        holder.addKey(expiredBeyondGraceKey());
        clearMetricData(holder);

        holder.evictExpired();

        assertThat(getMetricData(holder)).isNotNull();
    }

    @Test
    void evictExpired_doesNotPopulateMetricData_whenNothingEvicted() {
        JwkSetHolder holder = holderWithRandomKey();
        clearMetricData(holder);

        holder.evictExpired(); // nothing to evict

        assertThat(getMetricData(holder)).isNull();
    }

    @Test
    void metricCallback_recordsAllPendingValuesAndClearsEntry() {
        JwkSetHolder holder = holderWithRandomKey();
        holder.recordKeyCounts(); // populates 3 entries (key count + expiration days + issued days)

        ObservableDoubleMeasurement mockMeasurement = mock(ObservableDoubleMeasurement.class);
        ReflectionTestUtils.invokeMethod(holder, "metricCallback", METRIC_REGISTRY_COUNTS, mockMeasurement);

        verify(mockMeasurement, times(3)).record(anyDouble(), any());
        assertThat(getMetricData(holder)).isNull(); // entry cleared after callback
    }

    // --- formatDays contract ---------------------------------------------------------------

    @Test
    void formatDays_negativeMillis_returnsZero() {
        assertThat(JwkSetHolder.formatDays(-1L)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(JwkSetHolder.formatDays(Long.MIN_VALUE)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void formatDays_zeroMillis_returnsZeroWithTwoDecimalScale() {
        BigDecimal result = JwkSetHolder.formatDays(0L);
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.scale()).isEqualTo(2); // sub-1-day branch sets scale 2
    }

    @Test
    void formatDays_subOneDay_isHalfUpToTwoDecimals() {
        // 1 hour = 0.0416… → HALF_UP at scale 2 → 0.04
        BigDecimal oneHour = JwkSetHolder.formatDays(60L * 60 * 1000);
        assertThat(oneHour).isEqualByComparingTo(new BigDecimal("0.04"));
        assertThat(oneHour.scale()).isEqualTo(2);

        // 12 hours = 0.5 exactly
        BigDecimal halfDay = JwkSetHolder.formatDays(12L * 60 * 60 * 1000);
        assertThat(halfDay).isEqualByComparingTo(new BigDecimal("0.50"));
        assertThat(halfDay.scale()).isEqualTo(2);
    }

    @Test
    void formatDays_atOneDayBoundary_isFlooredWholeNumber() {
        // exactly 1 day → whole-day FLOOR branch → 1, scale 0
        BigDecimal oneDay = JwkSetHolder.formatDays(24L * 60 * 60 * 1000);
        assertThat(oneDay).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(oneDay.scale()).isZero();
    }

    @Test
    void formatDays_aboveOneDay_truncatesFractionsViaFloor() {
        // 1 day + 23h59m → still floors to 1 (not rounds to 2)
        long almostTwoDays = 24L * 60 * 60 * 1000 + (23L * 60 + 59) * 60 * 1000;
        BigDecimal result = JwkSetHolder.formatDays(almostTwoDays);
        assertThat(result).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(result.scale()).isZero();
    }

    // --- Clock injection wiring ------------------------------------------------------------

    @Test
    void evictExpired_usesInjectedClock_notSystemTime() {
        // Clock advanced 24h beyond NOW → grace period exceeded → recently-expired key gets evicted
        Clock futureClock = Clock.fixed(NOW.plus(Duration.ofHours(24)), ZoneOffset.UTC);
        JwkSetHolder holder = holderWithRandomKey(futureClock);
        // Add a key that expired 1 hour before NOW (within 12h grace at NOW, but beyond grace at NOW+24h)
        RSAKey recentlyExpired = expiredKey();
        holder.addKey(recentlyExpired);

        List<RSAKey> evicted = holder.evictExpired();

        assertThat(evicted).containsExactly(recentlyExpired);
        assertThat(holder.getAll().getKeys()).doesNotContain(recentlyExpired);
    }

    @Test
    void recordKeyCounts_dayMetrics_useInjectedClock() {
        // Key issued at a fixed instant; expires 10 days later
        Instant issuedAt = Instant.parse("2025-01-01T00:00:00Z");
        Instant expiresAt = issuedAt.plus(Duration.ofDays(10));

        // Pin "now" to issuedAt + 3 days → 3 days elapsed, 7 days remaining
        Clock pinned = Clock.fixed(issuedAt.plus(Duration.ofDays(3)), ZoneOffset.UTC);
        JwkSetHolder holder = holderWithRandomKey(pinned);
        holder.addKey(keyWithIssueAndExpiry(issuedAt, expiresAt));

        Map<String, BigDecimalMetricData> data = getMetricData(holder);
        BigDecimalMetricData expEntry = data.get(TYPE_RSA_KEY_EXPIRATION_DAYS);
        BigDecimalMetricData issEntry = data.get(TYPE_RSA_KEY_ISSUED_DAYS);
        assertThat(expEntry.getValue()).isEqualByComparingTo(BigDecimal.valueOf(7));   // expiration days
        assertThat(expEntry.getValue().scale()).isZero();
        assertThat(issEntry.getValue()).isEqualByComparingTo(BigDecimal.valueOf(3));   // issued days
        assertThat(issEntry.getValue().scale()).isZero();
    }

    @Test
    void recordKeyCounts_expirationDays_clampsToZero_whenAlreadyExpired() {
        Instant issuedAt = Instant.parse("2025-01-01T00:00:00Z");
        Instant expiresAt = issuedAt.plus(Duration.ofDays(10));

        // Pin "now" past expiry → expiration delta is negative → clamped to 0
        Clock pastExpiry = Clock.fixed(expiresAt.plus(Duration.ofHours(1)), ZoneOffset.UTC);
        JwkSetHolder holder = holderWithRandomKey(pastExpiry);
        holder.addKey(keyWithIssueAndExpiry(issuedAt, expiresAt));

        Map<String, BigDecimalMetricData> data = getMetricData(holder);
        assertThat(data.get(TYPE_RSA_KEY_EXPIRATION_DAYS).getValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void metricCallback_doesNotRecordAnything_whenNoPendingData() {
        JwkSetHolder holder = holderWithRandomKey();
        // map is empty — recordKeyCounts() was never called

        ObservableDoubleMeasurement mockMeasurement = mock(ObservableDoubleMeasurement.class);
        ReflectionTestUtils.invokeMethod(holder, "metricCallback", METRIC_REGISTRY_COUNTS, mockMeasurement);

        verifyNoInteractions(mockMeasurement);
    }

    /** Key with both an issueTime and an expirationTime pinned to fixed instants. */
    private RSAKey keyWithIssueAndExpiry(Instant issuedAt, Instant expiresAt) {
        try {
            RSAKey base = Jwks.generateRsa();
            return new RSAKey.Builder(base.toRSAPublicKey())
                    .privateKey(base.toRSAPrivateKey())
                    .keyID(base.getKeyID() + "-fixed")
                    .issueTime(Date.from(issuedAt))
                    .notBeforeTime(Date.from(issuedAt))
                    .expirationTime(Date.from(expiresAt))
                    .build();
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    /** Key with an issueTime offset by {@code millisAgo} from NOW (positive = in the past). */
    private RSAKey keyWithIssueTime(long millisAgo, String kidSuffix) {
        try {
            RSAKey base = Jwks.generateRsa();
            Instant issueInstant = NOW.minusMillis(millisAgo);
            return new RSAKey.Builder(base.toRSAPublicKey())
                    .privateKey(base.toRSAPrivateKey())
                    .keyID(base.getKeyID() + kidSuffix)
                    .issueTime(Date.from(issueInstant))
                    .notBeforeTime(Date.from(issueInstant))
                    .build();
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    /** Key with an expirationTime offset by {@code millisFromNow} from NOW (positive = future). */
    private RSAKey keyWithExpirationTime(long millisFromNow, String kidSuffix) {
        try {
            RSAKey base = Jwks.generateRsa();
            return new RSAKey.Builder(base.toRSAPublicKey())
                    .privateKey(base.toRSAPrivateKey())
                    .keyID(base.getKeyID() + kidSuffix)
                    .expirationTime(Date.from(NOW.plusMillis(millisFromNow)))
                    .build();
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    /** Key whose cert expired 1 hour before NOW — within the 12h default grace period. */
    private RSAKey expiredKey() {
        try {
            RSAKey base = Jwks.generateRsa();
            return new RSAKey.Builder(base.toRSAPublicKey())
                    .privateKey(base.toRSAPrivateKey())
                    .keyID(base.getKeyID() + "-expired")
                    .expirationTime(Date.from(NOW.minus(Duration.ofHours(1))))
                    .build();
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    /** Key whose cert expired 13 hours before NOW — beyond the 12h default grace period. */
    private RSAKey expiredBeyondGraceKey() {
        try {
            RSAKey base = Jwks.generateRsa();
            return new RSAKey.Builder(base.toRSAPublicKey())
                    .privateKey(base.toRSAPrivateKey())
                    .keyID(base.getKeyID() + "-expired-beyond-grace")
                    .expirationTime(Date.from(NOW.minus(Duration.ofHours(13))))
                    .build();
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, BigDecimalMetricData> getMetricData(JwkSetHolder holder) {
        Map<String, Map<String, BigDecimalMetricData>> map = (Map<String, Map<String, BigDecimalMetricData>>)
                ReflectionTestUtils.getField(holder, "unprocessedStatsValues");
        return map != null ? map.get(METRIC_REGISTRY_COUNTS) : null;
    }

    @SuppressWarnings("unchecked")
    private void clearMetricData(JwkSetHolder holder) {
        Map<String, Map<String, BigDecimalMetricData>> map = (Map<String, Map<String, BigDecimalMetricData>>)
                ReflectionTestUtils.getField(holder, "unprocessedStatsValues");
        if (map != null) map.clear();
    }
}
