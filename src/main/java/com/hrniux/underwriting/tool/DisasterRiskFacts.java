package com.hrniux.underwriting.tool;

import java.time.LocalDate;
import java.util.Objects;

public record DisasterRiskFacts(
        String policyNo,
        String riskZone,
        HazardLevel rainstorm,
        HazardLevel flood,
        HazardLevel fire,
        LocalDate dataDate) {

    public DisasterRiskFacts {
        Objects.requireNonNull(policyNo, "policyNo must not be null");
        Objects.requireNonNull(riskZone, "riskZone must not be null");
        Objects.requireNonNull(rainstorm, "rainstorm must not be null");
        Objects.requireNonNull(flood, "flood must not be null");
        Objects.requireNonNull(fire, "fire must not be null");
        Objects.requireNonNull(dataDate, "dataDate must not be null");
    }
}
