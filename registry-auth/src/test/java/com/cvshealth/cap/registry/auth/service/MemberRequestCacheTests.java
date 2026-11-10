// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.service;

import com.cvshealth.cap.registry.auth.model.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class MemberRequestCacheTests {

    private MemberRequestCache cache;

    @BeforeEach
    public void setUp() {
        cache = new MemberRequestCache();
    }

    @Test
    public void get_returnsCacheMiss_whenCacheIsEmpty() {
        Optional<Optional<Member>> result = cache.get("ext-001", false);

        assertThat(result).isEmpty();
    }

    @Test
    public void get_returnsCacheHit_withPresentMember() {
        Member member = Member.builder().externalId("ext-001").build();
        cache.put("ext-001", false, Optional.of(member));

        Optional<Optional<Member>> result = cache.get("ext-001", false);

        assertThat(result).isPresent();
        assertThat(result.get()).contains(member);
    }

    @Test
    public void get_returnsCacheHit_withEmptyMember() {
        cache.put("ext-001", false, Optional.empty());

        Optional<Optional<Member>> result = cache.get("ext-001", false);

        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    public void get_returnsCacheMiss_whenFullGraphFlagDiffers() {
        Member member = Member.builder().externalId("ext-001").build();
        cache.put("ext-001", true, Optional.of(member));

        Optional<Optional<Member>> result = cache.get("ext-001", false);

        assertThat(result).isEmpty();
    }

    @Test
    public void get_returnsCacheMiss_whenExternalIdDiffers() {
        Member member = Member.builder().externalId("ext-001").build();
        cache.put("ext-001", false, Optional.of(member));

        Optional<Optional<Member>> result = cache.get("ext-002", false);

        assertThat(result).isEmpty();
    }

    @Test
    public void get_returnsCacheHit_forFullGraphKey() {
        Member member = Member.builder().externalId("ext-001").build();
        cache.put("ext-001", true, Optional.of(member));

        Optional<Optional<Member>> result = cache.get("ext-001", true);

        assertThat(result).isPresent();
        assertThat(result.get()).contains(member);
    }

    @Test
    public void put_overwritesExistingEntry() {
        Member first = Member.builder().externalId("ext-001").firstName("Alice").build();
        Member second = Member.builder().externalId("ext-001").firstName("Bob").build();

        cache.put("ext-001", false, Optional.of(first));
        cache.put("ext-001", false, Optional.of(second));

        Optional<Optional<Member>> result = cache.get("ext-001", false);

        assertThat(result).isPresent();
        assertThat(result.get()).contains(second);
    }

    @Test
    public void get_returnsCorrectEntry_forDistinctKeys() {
        Member memberA = Member.builder().externalId("ext-001").build();
        Member memberB = Member.builder().externalId("ext-002").build();

        cache.put("ext-001", false, Optional.of(memberA));
        cache.put("ext-002", false, Optional.of(memberB));

        assertThat(cache.get("ext-001", false).get()).contains(memberA);
        assertThat(cache.get("ext-002", false).get()).contains(memberB);
    }

    @Test
    public void get_treatsFullGraphTrueAndFalseAsDistinctKeys() {
        Member memberFull = Member.builder().externalId("ext-001").firstName("Full").build();
        Member memberPartial = Member.builder().externalId("ext-001").firstName("Partial").build();

        cache.put("ext-001", true, Optional.of(memberFull));
        cache.put("ext-001", false, Optional.of(memberPartial));

        assertThat(cache.get("ext-001", true).get()).contains(memberFull);
        assertThat(cache.get("ext-001", false).get()).contains(memberPartial);
    }
}
