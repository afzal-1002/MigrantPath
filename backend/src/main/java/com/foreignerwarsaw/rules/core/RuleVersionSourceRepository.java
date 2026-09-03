package com.foreignerwarsaw.rules.core;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleVersionSourceRepository
    extends JpaRepository<RuleVersionSource, RuleVersionSourceId> {

  List<RuleVersionSource> findByRuleVersion_Id(UUID ruleVersionId);
}
