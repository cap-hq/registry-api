// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.model.exception;

public enum ExceptionReason {
    // General
    UNEXPECTED_ERROR(null),
    ACCESS_DENIED("Access denied"),
    FILTER_REQUIRED("Filter is required"),
    FILTER_TYPE_NOT_SUPPORTED("Filter search type '%s' is not supported for non Registry members only rollups"),
    FILTER_PAGE_SIZE_NON_APPREG_LIMIT("Page size is limited to %s for non Registry members only rollups"),
    FILTER_PAGE_NON_APPREG_LIMIT("Page limit is 1 for non Registry members only rollups"),
    FILTER_REQUEST_NON_APPREG_LIMIT("Filter search is limited to %s requests for non Registry members only rollups"),
    INVALID_MANAGER_TITLE("The manager title found is invalid: %s"),

    // User
    USER_SEARCH_REQUIRED("User search is required"),
    USER_SEARCH_INVALID_LENGTH("User search min length is %s");

    public final String label;

    ExceptionReason(String label) {
        this.label = label;
    }
}
