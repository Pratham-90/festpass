package com.festpass.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("""
            select u from User u
            where lower(u.name) like lower(concat('%', :q, '%')) or u.email like lower(concat('%', :q, '%'))
            order by u.createdAt desc
            """)
    List<User> search(String q);

    List<User> findAllByOrderByCreatedAtDesc();
}
