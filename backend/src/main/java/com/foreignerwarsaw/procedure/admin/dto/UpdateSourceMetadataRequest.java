package com.foreignerwarsaw.procedure.admin.dto;

import java.util.UUID;

/**
 * Pre-Phase-10 hardening (brief §C) - deliberately excludes {@code title}/{@code sourceUrl}/ {@code
 * sourceType}, which have no update path at all (create a new source instead if those material
 * identity fields need to change). {@code authorityId} is guarded by {@code
 * OfficialSourceService#assertIdentityEditable} once the source has backed published content;
 * {@code jurisdictionCode}/{@code language} are always editable operational metadata.
 */
public record UpdateSourceMetadataRequest(
    UUID authorityId, String jurisdictionCode, String language) {}
