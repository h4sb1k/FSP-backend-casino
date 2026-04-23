package ru.fsp.casino.game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.fsp.casino.domain.enums.RoomStatus;
import ru.fsp.casino.domain.model.Room;
import ru.fsp.casino.domain.model.RoomParticipant;
import ru.fsp.casino.domain.model.User;
import ru.fsp.casino.domain.repository.UserRepository;
import ru.fsp.casino.game.service.BalanceService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BalanceService balanceService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
            .id(1L)
            .username("test")
            .balance(1000L)
            .reservedBalance(0L)
            .build();
    }

    @Test
    void reserve_sufficientBalance_deductsFromAvailable() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        balanceService.reserve(1L, 500L);

        assertEquals(500L, user.getBalance());
        assertEquals(500L, user.getReservedBalance());
    }

    @Test
    void reserve_insufficientBalance_throwsException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(BalanceService.InsufficientBalanceException.class,
            () -> balanceService.reserve(1L, 1500L));
    }

    @Test
    void release_returnsToAvailable() {
        user.setBalance(500L);
        user.setReservedBalance(500L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        balanceService.release(1L, 500L);

        assertEquals(1000L, user.getBalance());
        assertEquals(0L, user.getReservedBalance());
    }

    @Test
    void settle_realWinner_creditsBalance() {
        Room room = Room.builder()
            .id(1L).entryFee(100L).prizePoolPct(80)
            .boostMultiplier(BigDecimal.valueOf(2.0)).build();

        User u1 = User.builder().id(1L).balance(0L).reservedBalance(100L).build();
        User u2 = User.builder().id(2L).balance(0L).reservedBalance(100L).build();
        User u3 = User.builder().id(3L).balance(0L).reservedBalance(100L).build();
        User u4 = User.builder().id(4L).balance(0L).reservedBalance(100L).build();

        RoomParticipant p1 = RoomParticipant.builder().id(1L).user(u1).isBot(false).boosted(false).build();
        RoomParticipant p2 = RoomParticipant.builder().id(2L).user(u2).isBot(false).boosted(false).build();
        RoomParticipant p3 = RoomParticipant.builder().id(3L).user(u3).isBot(false).boosted(false).build();
        RoomParticipant p4 = RoomParticipant.builder().id(4L).user(u4).isBot(false).boosted(false).build();

        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        long payout = balanceService.settle(room, List.of(p1, p2, p3, p4), p1);

        // 4 real * 100 = 400 pool, 80% = 320 payout
        assertEquals(320L, payout);
        assertEquals(320L, u1.getBalance());
        assertEquals(0L, u2.getBalance());
    }

    @Test
    void settle_botWinner_noBalanceChange() {
        Room room = Room.builder()
            .id(1L).entryFee(100L).prizePoolPct(80)
            .boostMultiplier(BigDecimal.valueOf(2.0)).build();

        User u1 = User.builder().id(1L).balance(0L).reservedBalance(100L).build();
        RoomParticipant realP = RoomParticipant.builder().id(1L).user(u1).isBot(false).build();
        RoomParticipant botP = RoomParticipant.builder().id(2L).user(null).isBot(true).botName("Борис_42").build();

        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        long payout = balanceService.settle(room, List.of(realP, botP), botP);

        assertEquals(80L, payout);
        assertEquals(0L, u1.getBalance()); // bot won, no credit
    }

    @Test
    void deduct_sufficientBalance_deductsDirectly() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        balanceService.deduct(1L, 50L);

        assertEquals(950L, user.getBalance());
        assertEquals(0L, user.getReservedBalance()); // reservedBalance untouched
    }
}
