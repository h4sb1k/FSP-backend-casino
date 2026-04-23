package ru.fsp.casino.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.fsp.casino.domain.enums.RoomStatus;
import ru.fsp.casino.domain.enums.VipTier;
import ru.fsp.casino.domain.model.Room;

import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByStatus(RoomStatus status);
    List<Room> findByStatusIn(List<RoomStatus> statuses);

    @Query("SELECT r FROM Room r WHERE r.status IN :statuses " +
           "AND (:tier IS NULL OR r.tier = :tier) " +
           "AND (:entryFeeMin IS NULL OR r.entryFee >= :entryFeeMin) " +
           "AND (:entryFeeMax IS NULL OR r.entryFee <= :entryFeeMax)")
    List<Room> findFiltered(
        @Param("statuses") List<RoomStatus> statuses,
        @Param("tier") VipTier tier,
        @Param("entryFeeMin") Long entryFeeMin,
        @Param("entryFeeMax") Long entryFeeMax
    );
}
