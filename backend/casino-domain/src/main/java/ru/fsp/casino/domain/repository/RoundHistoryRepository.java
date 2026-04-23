package ru.fsp.casino.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.fsp.casino.domain.model.RoundHistory;

import java.util.List;

public interface RoundHistoryRepository extends JpaRepository<RoundHistory, Long> {
    List<RoundHistory> findByOrderByFinishedAtDesc(Pageable pageable);
    List<RoundHistory> findByRoomIdOrderByFinishedAtDesc(Long roomId, Pageable pageable);
    List<RoundHistory> findByWinnerUserIdOrderByFinishedAtDesc(Long userId, Pageable pageable);
}
