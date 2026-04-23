package ru.fsp.casino.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.fsp.casino.domain.enums.RoomStatus;
import ru.fsp.casino.domain.model.RoomParticipant;

import java.util.List;
import java.util.Optional;

public interface RoomParticipantRepository extends JpaRepository<RoomParticipant, Long> {
    List<RoomParticipant> findByRoom_Id(Long roomId);
    Optional<RoomParticipant> findByRoom_IdAndUser_Id(Long roomId, Long userId);
    boolean existsByRoom_IdAndUser_Id(Long roomId, Long userId);

    @Query("SELECT rp FROM RoomParticipant rp " +
           "JOIN rp.room r " +
           "WHERE rp.user.id = :userId " +
           "AND r.status IN :statuses")
    List<RoomParticipant> findActiveByUserId(
        @Param("userId") Long userId,
        @Param("statuses") List<RoomStatus> statuses
    );
}
