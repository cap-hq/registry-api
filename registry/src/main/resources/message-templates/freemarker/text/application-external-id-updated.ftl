<#-- SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
SPDX-License-Identifier: Apache-2.0 -->Your Registry team, ${teamName}, currently owns the Registry application '${applicationName}'. We've determined that in ServiceNow, the associated APM application, '${externalId}', no longer exists.

The external ID for your application has been unset in Registry.  Before any new production deployments can be done in CAP, the correct APM ID will need to be added.<#if docsUrl != "UNDEFINED">  Documentation on setting the application external ID can be found at, ${docsUrl}</#if>

${footer}