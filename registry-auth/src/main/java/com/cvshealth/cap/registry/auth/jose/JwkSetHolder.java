// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.jose;

import com.cvshealth.cap.registry.auth.metric.BigDecimalMetricData;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Thread-safe holder for all currently valid RSA signing keys, ordered newest-first.
 * <p>
 * Holding multiple keys simultaneously allows {@code NimbusJwtEncoder} to sign with the newest key
 * (first result from {@link #getAll()}) while {@code NimbusJwtDecoder} can still verify tokens
 * signed with older keys via kid-matched selection.
 */
@Component
@Slf4j
public class JwkSetHolder {

    private static final String METRIC_SCOPE_NAME = "auth.event";
    public static final String METRIC_REGISTRY_COUNTS = "auth.counts";
    private static final BigDecimal MILLIS_PER_DAY = BigDecimal.valueOf(86_400_000L);

    /** Metric "type" attribute values; also the map keys consumers use to look up entries. */
    public static final String TYPE_RSA_KEYS = "RSA Keys";
    public static final String TYPE_RSA_KEY_EXPIRATION_DAYS = "RSA Key Expiration Days";
    public static final String TYPE_RSA_KEY_ISSUED_DAYS = "RSA Key Issued Days";
    private static final AttributeKey<String> TYPE_KEY = AttributeKey.stringKey("type");

    private final CopyOnWriteArrayList<RSAKey> keys = new CopyOnWriteArrayList<>();
    /** Per-metric map of type-label → most recent data point. Outer key is the metric name. */
    private final Map<String, Map<String, BigDecimalMetricData>> unprocessedStatsValues = new ConcurrentHashMap<>();

    /** Time source; injected for deterministic eviction / metric assertions. */
    private final Clock clock;

    /**
     * Grace period added to a key's certificate expiry before eviction. Ensures tokens signed by
     * an expiring key remain verifiable for the duration of the longest-lived token type
     * (refresh token TTL by default).
     */
    @Value("${app.ssl.rotation.key-grace-period:PT12H}")
    private Duration keyGracePeriod = Duration.ofHours(12);

    @Value("${app.metrics.prefix}")
    private String metricPrefix;

    @PostConstruct
    private void init() {
        String scopeName = metricPrefix + "." + METRIC_SCOPE_NAME;
        Meter meter = GlobalOpenTelemetry.meterBuilder(scopeName).build();
        meter.gaugeBuilder(metricPrefix + "." + METRIC_REGISTRY_COUNTS)
                .setDescription(String.format("AppReg Auth gauge counts %s", scopeName))
                .setUnit("number")
                .buildWithCallback(observableMeasurement -> metricCallback(METRIC_REGISTRY_COUNTS, observableMeasurement));
    }

    @Autowired
    public JwkSetHolder(@Nullable SslBundles sslBundles,
                        @Value("${app.ssl.rotation.create-legacy-key:false}") boolean createLegacyKey,
                        @Value("${app.ssl.rotation.legacy-cert-not-before-cutoff:#{null}}") @Nullable Instant legacyCertNotBeforeCutoff,
                        @Value("${app.ssl.rotation.legacy-key-id:registry-key}") String legacyKeyId) {
        this(sslBundles, createLegacyKey, legacyCertNotBeforeCutoff, legacyKeyId, Clock.systemUTC());
    }

    /** Package-private constructor for tests that need a deterministic clock. */
    JwkSetHolder(@Nullable SslBundles sslBundles,
                 boolean createLegacyKey,
                 @Nullable Instant legacyCertNotBeforeCutoff,
                 String legacyKeyId,
                 Clock clock) {
        this.clock = clock;
        SslBundle bundle = getOauthBundle(sslBundles);
        RSAKey rsaKey = (bundle != null) ? Jwks.generateRsa(bundle) : Jwks.generateRsa();
        keys.add(rsaKey);

        if (createLegacyKey && legacyCertNotBeforeCutoff != null && rsaKey.getNotBeforeTime() != null
                && rsaKey.getNotBeforeTime().toInstant().isBefore(legacyCertNotBeforeCutoff)) {
            log.info("JwkSetHolder: adding RSA Key for legacy alias kid={}, notBefore={}", rsaKey.getKeyID(), rsaKey.getNotBeforeTime());
            RSAKey rsaLegacyAliasKey = Jwks.generateRsaForLegacyAlias(rsaKey, legacyKeyId);
            if (rsaLegacyAliasKey != null) {
                keys.add(rsaLegacyAliasKey);
            }
        }
    }

    /** Returns a {@link JWKSet} of all currently valid keys, newest first. */
    public JWKSet getAll() {
        return new JWKSet(new ArrayList<>(keys));
    }

    /** Prepends {@code newKey} to the front so it is used for signing immediately. */
    public void addKey(RSAKey newKey) {
        keys.add(0, newKey);
        log.info("JwkSetHolder: added new key kid={}, total keys={}", newKey.getKeyID(), keys.size());
        recordKeyCounts();
    }

    /** Removes keys whose expiry plus the grace period has passed and returns the evicted keys. */
    public List<RSAKey> evictExpired() {
        Instant evictBefore = Instant.now(clock);
        List<RSAKey> evicted = keys.stream()
                .filter(key -> key.getExpirationTime() != null
                        && key.getExpirationTime().toInstant().plus(keyGracePeriod).isBefore(evictBefore))
                .toList();
        keys.removeAll(evicted);
        if (!evicted.isEmpty()) {
            log.info("JwkSetHolder: evicted {} expired key(s), remaining={}", evicted.size(), keys.size());
            recordKeyCounts();
        }
        return evicted;
    }

    @Scheduled(fixedDelayString = "${app.ssl.rotation.metric-publish-interval:PT12H}")
    public void recordKeyCounts() {
        Instant now = Instant.now(clock);
        List<RSAKey> snapshot = new ArrayList<>(keys);
        RSAKey newest = snapshot.isEmpty() ? null : snapshot.get(0);
        Map<String, BigDecimalMetricData> entries = new LinkedHashMap<>();
        entries.put(TYPE_RSA_KEYS, metric(TYPE_RSA_KEYS, BigDecimal.valueOf(snapshot.size())));
        entries.put(TYPE_RSA_KEY_EXPIRATION_DAYS,
                metric(TYPE_RSA_KEY_EXPIRATION_DAYS, daysUntil(newest, RSAKey::getExpirationTime, now)));
        entries.put(TYPE_RSA_KEY_ISSUED_DAYS,
                metric(TYPE_RSA_KEY_ISSUED_DAYS, daysSince(newest, RSAKey::getNotBeforeTime, now)));
        unprocessedStatsValues.put(METRIC_REGISTRY_COUNTS, Map.copyOf(entries));
    }

    private static BigDecimalMetricData metric(String typeLabel, BigDecimal value) {
        return new BigDecimalMetricData(value, Attributes.of(TYPE_KEY, typeLabel));
    }

    private SslBundle getOauthBundle(@Nullable SslBundles bundles) {
        if (bundles == null) return null;
        try {
            return bundles.getBundle("oauth");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Computes days remaining until a key's timestamp (e.g., expiration).
     * Negative results (already past) are clamped to zero.
     */
    private BigDecimal daysUntil(@Nullable RSAKey key, Function<RSAKey, Date> dateAccessor, Instant now) {
        if (key == null) return BigDecimal.ZERO;
        Date date = dateAccessor.apply(key);
        if (date == null) return BigDecimal.ZERO;
        long millis = Duration.between(now, date.toInstant()).toMillis();
        return formatDays(millis);
    }

    /**
     * Computes days elapsed since a key's timestamp (e.g., notBefore / issue time).
     * If the timestamp is in the future (clock skew), returns a negative day count
     * so callers can distinguish "not yet valid" from "just issued."
     */
    private BigDecimal daysSince(@Nullable RSAKey key, Function<RSAKey, Date> dateAccessor, Instant now) {
        if (key == null) return BigDecimal.ZERO;
        Date date = dateAccessor.apply(key);
        if (date == null) return BigDecimal.ZERO;
        long millis = Duration.between(date.toInstant(), now).toMillis();
        if (millis < 0) {
            return formatDays(-millis).negate();
        }
        return formatDays(millis);
    }

    /**
     * Formats a duration in milliseconds as a day-count {@link BigDecimal}.
     * <ul>
     *   <li>≥ 1 day → truncated whole days (e.g., 30, 29)</li>
     *   <li>&lt; 1 day → rounded to 2 decimal places (e.g., 0.04 ≈ 1 hour)</li>
     *   <li>negative (clock skew, already-expired) → 0</li>
     * </ul>
     */
    static BigDecimal formatDays(long millis) {
        if (millis < 0) return BigDecimal.ZERO;
        BigDecimal days = BigDecimal.valueOf(millis).divide(MILLIS_PER_DAY, 4, RoundingMode.HALF_UP);
        if (days.compareTo(BigDecimal.ONE) < 0) {
            return days.setScale(2, RoundingMode.HALF_UP);
        }
        return days.setScale(0, RoundingMode.FLOOR);
    }

    /**
     * Metric collector callback that records metric values only when data is present for a given metric.
     * This overrides the default behavior of sending the last recorded metric value, regardless of whether it has been refreshed during the collector periodic interval.
     * The default behavior is not desired for global metrics, where any instance of the service can populate the metric,
     * and an individual instance last recorded metric value may be out of date.
     */
    private void metricCallback(String metricName, ObservableDoubleMeasurement observableMeasurement) {
        Map<String, BigDecimalMetricData> metricValues = unprocessedStatsValues.remove(metricName);
        if (metricValues != null) {
            for (BigDecimalMetricData metricData : metricValues.values()) {
                if (log.isDebugEnabled()) {
                    log.debug(String.format("record metric [%s = %s]", metricName, metricData.toString()));
                }
                observableMeasurement.record(metricData.getValue().doubleValue(), metricData.getAttributes());
            }
        }
    }
}
