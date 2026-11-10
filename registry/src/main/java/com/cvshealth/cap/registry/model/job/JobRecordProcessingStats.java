// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.job;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NoArgsConstructor
@Getter
@Setter
@ToString
public class JobRecordProcessingStats {
    private int recordsProcessed;
    private int recordsDeleted;
    private int recordsUpdated;
    private int recordsErrored;
    private int recordDeleteErrors;

    public void addRecordsProcessed(int count) {
        recordsProcessed += count;
    }

    public void recordDeleted() {
        recordsDeleted += 1;
    }

    public void recordUpdated() {
        recordsUpdated += 1;
    }

    public void recordErrored() {
        recordsErrored += 1;
    }

    public void recordDeleteError() {
        recordDeleteErrors += 1;
    }

    public void recordBatch(JobRecordProcessingStats batchStats) {
        recordsProcessed += batchStats.getRecordsProcessed();
        recordsUpdated += batchStats.getRecordsUpdated();
        recordsErrored += batchStats.getRecordsErrored();
        recordsDeleted += batchStats.getRecordsDeleted();
        recordDeleteErrors += batchStats.getRecordDeleteErrors();
    }
}
