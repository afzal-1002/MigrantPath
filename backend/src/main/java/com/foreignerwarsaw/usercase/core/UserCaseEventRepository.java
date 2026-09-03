package com.foreignerwarsaw.usercase.core;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCaseEventRepository extends JpaRepository<UserCaseEvent, UUID> {

  List<UserCaseEvent> findByUserCase_IdOrderByOccurredAtDesc(UUID userCaseId);
}
