package com.foreignerwarsaw.procedure.admin.dto;

/**
 * What published content depends on this source (brief §33/§34) - admin should know the impact
 * before marking a source OUTDATED.
 */
public record SourceUsageResponse(
    long procedureVersions, long ruleVersions, long thresholdVersions) {

  public long total() {
    return procedureVersions + ruleVersions + thresholdVersions;
  }
}
