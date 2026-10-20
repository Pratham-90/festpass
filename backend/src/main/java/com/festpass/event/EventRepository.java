package com.festpass.event;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

    Optional<Event> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Event> findByOrganizerIdAndSandboxFalseOrderByStartsAtDesc(Long organizerId);

    List<Event> findBySandboxFalseOrderByStartsAtDesc();

    /** Locks the event row so layout changes (tiers, sections) for one event happen one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(Long id);
}
