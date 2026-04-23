package ru.fsp.casino.game.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.fsp.casino.domain.model.Room;
import ru.fsp.casino.domain.model.RoomParticipant;
import ru.fsp.casino.domain.model.User;
import ru.fsp.casino.domain.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BalanceService {

    private final UserRepository userRepository;

    @Transactional
    public void reserve(Long userId, Long amount) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        long available = user.getBalance() - user.getReservedBalance();
        if (available < amount) {
            throw new InsufficientBalanceException(
                "Insufficient balance: available=" + available + ", required=" + amount);
        }
        user.setBalance(user.getBalance() - amount);
        user.setReservedBalance(user.getReservedBalance() + amount);
        userRepository.save(user);
    }

    @Transactional
    public void release(Long userId, Long amount) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setBalance(user.getBalance() + amount);
        user.setReservedBalance(user.getReservedBalance() - amount);
        userRepository.save(user);
    }

    @Transactional
    public void deduct(Long userId, Long amount) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (user.getBalance() < amount) {
            throw new InsufficientBalanceException(
                "Insufficient balance: balance=" + user.getBalance() + ", required=" + amount);
        }
        user.setBalance(user.getBalance() - amount);
        userRepository.save(user);
    }

    @Transactional
    public long settle(Room room, List<RoomParticipant> participants, RoomParticipant winnerParticipant) {
        long realCount = participants.stream().filter(p -> !Boolean.TRUE.equals(p.getIsBot())).count();
        long totalPool = realCount * room.getEntryFee();
        long payout = (long) (totalPool * (room.getPrizePoolPct() / 100.0));

        for (RoomParticipant p : participants) {
            if (!Boolean.TRUE.equals(p.getIsBot()) && p.getUser() != null) {
                User u = p.getUser();
                u.setReservedBalance(u.getReservedBalance() - room.getEntryFee());
                userRepository.save(u);
            }
        }

        if (!Boolean.TRUE.equals(winnerParticipant.getIsBot()) && winnerParticipant.getUser() != null) {
            User winner = winnerParticipant.getUser();
            winner.setBalance(winner.getBalance() + payout);
            userRepository.save(winner);
        }

        return payout;
    }

    public static class InsufficientBalanceException extends RuntimeException {
        public InsufficientBalanceException(String message) {
            super(message);
        }
    }
}
