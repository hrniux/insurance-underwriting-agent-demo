package com.hrniux.underwriting.rule;

public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static RiskLevel fromScore(int score) {
        if (score < 30) {
            return LOW;
        }
        if (score < 60) {
            return MEDIUM;
        }
        if (score < 80) {
            return HIGH;
        }
        return CRITICAL;
    }
}
