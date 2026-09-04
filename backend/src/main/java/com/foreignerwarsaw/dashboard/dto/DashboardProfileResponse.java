package com.foreignerwarsaw.dashboard.dto;

/**
 * {@code city} is a fixed "Warsaw" literal, not a stored preference - this product is deliberately
 * Warsaw-first for V1 (PRODUCT_REQUIREMENTS.md §3); never fabricated per-user location data.
 */
public record DashboardProfileResponse(
    String displayName, String email, boolean accountVerified, String city) {}
