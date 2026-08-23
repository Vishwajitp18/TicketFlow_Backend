package com.project.ticketflow.repository;

import com.project.ticketflow.entity.Session;
import com.project.ticketflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {

    List<Session> findByUserOrderByLastUsedAtAsc(User user);

    @Query("""
            select s from Session s join fetch s.user u
            where u.id = :userId and
            s.familyId = :familyId
            """)
    Optional<Session> findSessionWithUser(
            @Param("userId") Long userId,
            @Param("familyId") String familyId);

    @Query("select s.jti from Session s where s.user.id = :userId")
    List<String> findAllJti(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Session s where s.user.id = :userId")
    void deleteAllSessionsForUser(@Param("userId") Long userId);

    Optional<Session> findByUserIdAndFamilyId(Long userId, String familyId);
}
