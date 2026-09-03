package com.foreignerwarsaw.admin.dto;

/** A single count-only impact figure (brief §72/§133) - never which specific users/records. */
public record ImpactCountResponse(long count, String description) {}
