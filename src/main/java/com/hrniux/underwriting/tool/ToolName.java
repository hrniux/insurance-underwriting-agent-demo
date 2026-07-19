package com.hrniux.underwriting.tool;

public enum ToolName {
    GET_POLICY(ToolCriticality.CRITICAL),
    GET_QUOTATION(ToolCriticality.CRITICAL),
    GET_UNDERWRITING_HISTORY(ToolCriticality.CRITICAL),
    GET_SURVEY_REPORT(ToolCriticality.CRITICAL),
    GET_DISASTER_RISK(ToolCriticality.DEGRADABLE),
    VALIDATE_RULES(ToolCriticality.CRITICAL);

    private final ToolCriticality criticality;

    ToolName(ToolCriticality criticality) {
        this.criticality = criticality;
    }

    public ToolCriticality criticality() {
        return criticality;
    }
}
