package com.festpass.checkin;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckinScanRepository extends JpaRepository<CheckinScan, Long> {

    Optional<CheckinScan> findFirstByEventIdOrderByScannedAtDescIdDesc(Long eventId);
}
