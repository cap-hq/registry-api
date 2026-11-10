// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.InformationClassificationBackfillJobConfig;
import com.cvshealth.cap.registry.model.InformationClassification;
import com.cvshealth.cap.registry.model.LegacyComplianceClassification;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.model.job.InformationClassificationBackfillResponse;
import com.cvshealth.cap.registry.model.job.InformationClassificationBackfillResponse.SkippedApplication;
import com.cvshealth.cap.registry.persistent.ApplicationEntity;
import com.cvshealth.cap.registry.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One-time fill of application.information_classification for rows created before the column existed.
 * Only rows that are still NULL are touched, so running it again changes nothing.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InformationClassificationBackfillService {
    private final InformationClassificationBackfillJobConfig config;
    private final ApplicationRepository applicationRepository;

    public InformationClassificationBackfillResponse backfill() {
        if (!config.isEnabled()) {
            throw new BadRequestException(ExceptionReason.INFORMATION_CLASSIFICATION_BACKFILL_DISABLED);
        }
        long scanned = 0;
        long updated = 0;
        long afterId = 0;
        List<SkippedApplication> skipped = new ArrayList<>();
        List<ApplicationEntity> batch;
        // walk by id so rows that stay NULL (unmapped tags) are not read again
        while (!(batch = applicationRepository.findUnclassifiedAfter(afterId, PageRequest.of(0, config.getBatchSize()))).isEmpty()) {
            Map<InformationClassification, List<Long>> idsByClassification = new EnumMap<>(InformationClassification.class);
            for (ApplicationEntity application : batch) {
                try {
                    idsByClassification.computeIfAbsent(
                            LegacyComplianceClassification.derive(application.getComplianceTypes()), key -> new ArrayList<>())
                            .add(application.getId());
                } catch (ApiException e) {
                    log.warn("Skipping application id={} name={}: {}", application.getId(), application.getName(), e.getMessage());
                    skipped.add(SkippedApplication.builder().id(application.getId()).name(application.getName())
                            .complianceTypes(application.getComplianceTypes()).build());
                }
            }
            for (Map.Entry<InformationClassification, List<Long>> entry : idsByClassification.entrySet()) {
                updated += applicationRepository.setClassificationWhereUnset(entry.getKey(), entry.getValue());
            }
            scanned += batch.size();
            afterId = batch.getLast().getId();
        }
        log.info("Information classification backfill: scanned={} updated={} skipped={}", scanned, updated, skipped.size());
        return InformationClassificationBackfillResponse.builder().scanned(scanned).updated(updated).skipped(skipped).build();
    }
}
