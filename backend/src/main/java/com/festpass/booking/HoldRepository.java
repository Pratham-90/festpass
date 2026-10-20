package com.festpass.booking;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldRepository extends JpaRepository<Hold, UUID> {}
