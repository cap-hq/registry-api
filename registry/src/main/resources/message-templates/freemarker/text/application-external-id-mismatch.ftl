<#-- SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
SPDX-License-Identifier: Apache-2.0 -->Your Registry team, ${teamName}, currently owns the Registry application '${applicationName}'. We've determined that your Registry team currently rolls up to '${rollupName}'. However, in ServiceNow, the associated APM application, '${externalId}', is currently listed under '${externalIdRollupName}'.

Please verify that the correct APM ID was used for your Registry application's external ID attribute.<#if docsUrl != "UNDEFINED"> If your APM application in ServiceNow has an outdated SVP listed, documentation on how to request a change to the listed SVP for your APM application can be found at, ${docsUrl}</#if>

${footer}