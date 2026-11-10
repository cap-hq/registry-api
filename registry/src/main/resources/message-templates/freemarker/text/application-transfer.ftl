<#-- SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
SPDX-License-Identifier: Apache-2.0 -->Application '${applicationName}' transfer from Team '${sourceTeamName}' to Team '${receiveTeamName}' is ${transferStatus}.
<#if transferComments != "">Comments: ${transferComments}</#if>
<#if transferStatus == "PENDING">The application transfer can be reviewed/accepted ${inRegistryOrCapUI}</#if>

${footer}