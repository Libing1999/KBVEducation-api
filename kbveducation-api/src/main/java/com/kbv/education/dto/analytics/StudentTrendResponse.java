package com.kbv.education.dto.analytics;

import java.util.List;
import java.util.UUID;

public record StudentTrendResponse(UUID studentId, String studentName, List<TrendPointResponse> points) {
}
