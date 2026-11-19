package com.housinglab.api.common;

import java.time.Instant;

public record ApiError(String code, String message, String traceId, Instant timestamp) {}
