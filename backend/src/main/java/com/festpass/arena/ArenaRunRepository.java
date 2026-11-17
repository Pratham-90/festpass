package com.festpass.arena;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArenaRunRepository extends JpaRepository<ArenaRun, Long> {

    List<ArenaRun> findTop10ByOrderByIdDesc();

    boolean existsByStatusIn(List<ArenaRun.Status> statuses);
}
