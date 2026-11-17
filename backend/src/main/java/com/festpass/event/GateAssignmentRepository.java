package com.festpass.event;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GateAssignmentRepository extends JpaRepository<GateAssignment, GateAssignment.Key> {

    List<GateAssignment> findByEventId(Long eventId);

    List<GateAssignment> findByUserId(Long userId);

    boolean existsByUserIdAndEventId(Long userId, Long eventId);
}
