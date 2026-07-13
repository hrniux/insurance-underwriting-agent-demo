package com.hrniux.underwriting.rule;

public enum Decision {
    APPROVE,
    MANUAL_REVIEW,
    REJECT;

    public static Decision strongest(Decision left, Decision right) {
        return left.ordinal() >= right.ordinal() ? left : right;
    }
}
