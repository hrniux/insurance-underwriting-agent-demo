package com.hrniux.underwriting.tool;

import java.util.List;
import java.util.Objects;

public record SurveyReportFacts(
        String policyNo,
        String fireProtectionStatus,
        boolean drainageRemediationCompleted,
        List<String> openIssues,
        String conclusion) {

    public SurveyReportFacts {
        Objects.requireNonNull(policyNo, "policyNo must not be null");
        Objects.requireNonNull(fireProtectionStatus, "fireProtectionStatus must not be null");
        openIssues = List.copyOf(openIssues);
        Objects.requireNonNull(conclusion, "conclusion must not be null");
    }
}
