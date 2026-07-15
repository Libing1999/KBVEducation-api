package com.kbv.education.dto.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TrendPointResponse(LocalDate date, BigDecimal value) {
}
