package com.festpass.payment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

    Optional<PaymentAttempt> findByIdempotencyKey(String idempotencyKey);
}
