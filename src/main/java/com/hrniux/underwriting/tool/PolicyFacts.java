package com.hrniux.underwriting.tool;

import java.time.LocalDate;
import java.util.Objects;

public record PolicyFacts(
        String policyNo,
        String productCode,
        String insuredName,
        String occupancy,
        String address,
        LocalDate startDate,
        LocalDate endDate) {

    public PolicyFacts {
        Objects.requireNonNull(policyNo, "policyNo must not be null");
        Objects.requireNonNull(productCode, "productCode must not be null");
        Objects.requireNonNull(insuredName, "insuredName must not be null");
        Objects.requireNonNull(occupancy, "occupancy must not be null");
        Objects.requireNonNull(address, "address must not be null");
        Objects.requireNonNull(startDate, "startDate must not be null");
        Objects.requireNonNull(endDate, "endDate must not be null");
    }
}
