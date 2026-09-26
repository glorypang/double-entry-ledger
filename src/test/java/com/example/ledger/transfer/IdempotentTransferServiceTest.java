package com.example.ledger.transfer;

import com.example.ledger.wallet.DepositService;
import com.example.ledger.wallet.Wallet;
import com.example.ledger.wallet.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class IdempotentTransferServiceTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IdempotentTransferService transferService;

    @Autowired
    private DepositService depositService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransferRequestRecordRepository recordRepository;

    private Long fromId;
    private Long toId;
    private String key;

    @BeforeEach
    void setUp() {
        fromId = walletRepository.saveAndFlush(
                new Wallet("테스트 송신자")
        ).getId();

        toId = walletRepository.saveAndFlush(
                new Wallet("테스트 수신자")
        ).getId();

        key = UUID.randomUUID().toString();

        depositService.deposit(fromId, 100000);
    }

    @Test
    void 같은_요청은_한번만_송금된다() {
        TransferRequest request =
                new TransferRequest(fromId, toId, 30000);

        transferService.transfer(key, request);
        transferService.transfer(key, request);

        assertEquals(70000L, balanceOf(fromId));
        assertEquals(30000L, balanceOf(toId));
        assertTrue(recordRepository.existsById(key));
    }

    @Test
    void 같은_키로_금액을_바꾸면_거절된다() {
        transferService.transfer(
                key,
                new TransferRequest(fromId, toId, 30000)
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> transferService.transfer(
                        key,
                        new TransferRequest(fromId, toId, 40000)
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        // 첫 번째 송금만 반영되어 있어야 한다.
        assertEquals(70000L, balanceOf(fromId));
        assertEquals(30000L, balanceOf(toId));
    }

    @Test
    void 실패한_요청은_충전후_같은_키로_재시도할수있다() {
        TransferRequest request =
                new TransferRequest(fromId, toId, 150000);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> transferService.transfer(key, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        // 실패하면 잔액과 요청 키 모두 원래 상태여야 한다.
        assertEquals(100000L, balanceOf(fromId));
        assertEquals(0L, balanceOf(toId));
        assertFalse(recordRepository.existsById(key));

        depositService.deposit(fromId, 50000);

        transferService.transfer(key, request);

        assertEquals(0L, balanceOf(fromId));
        assertEquals(150000L, balanceOf(toId));
        assertTrue(recordRepository.existsById(key));
    }

    @Test
    void 같은_요청을_동시에_보내도_한번만_송금된다() throws Exception {
        TransferRequest request =
                new TransferRequest(fromId, toId, 30000);

        var executor = Executors.newFixedThreadPool(2);

        // 두 작업이 모두 준비됐는지 확인한다.
        CountDownLatch ready = new CountDownLatch(2);

        // 준비된 작업을 함께 출발시킨다.
        CountDownLatch start = new CountDownLatch(1);

        try {
            var first = executor.submit(() -> {
                ready.countDown();

                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("시작 신호 대기 시간 초과");
                }

                transferService.transfer(key, request);
                return true;
            });

            var second = executor.submit(() -> {
                ready.countDown();

                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("시작 신호 대기 시간 초과");
                }

                transferService.transfer(key, request);
                return true;
            });

            assertTrue(
                    ready.await(5, TimeUnit.SECONDS),
                    "두 작업이 준비되지 않았습니다."
            );

            start.countDown();

            // 두 요청 모두 정상 완료되어야 한다.
            assertTrue(first.get(20, TimeUnit.SECONDS));
            assertTrue(second.get(20, TimeUnit.SECONDS));

            // 실제 송금은 한 번만 반영되어야 한다.
            assertEquals(70000L, balanceOf(fromId));
            assertEquals(30000L, balanceOf(toId));
            assertTrue(recordRepository.existsById(key));
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void 동시에_출금해도_잔액을_초과할수없다() throws Exception {
        TransferRequest request =
                new TransferRequest(fromId, toId, 70000);

        String firstKey = UUID.randomUUID().toString();
        String secondKey = UUID.randomUUID().toString();

        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            var first = executor.submit(() -> {
                ready.countDown();

                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("시작 신호 대기 시간 초과");
                }

                return attemptTransfer(firstKey, request);
            });

            var second = executor.submit(() -> {
                ready.countDown();

                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("시작 신호 대기 시간 초과");
                }

                return attemptTransfer(secondKey, request);
            });

            assertTrue(
                    ready.await(5, TimeUnit.SECONDS),
                    "두 작업이 준비되지 않았습니다."
            );

            start.countDown();

            boolean firstSucceeded = first.get(20, TimeUnit.SECONDS);
            boolean secondSucceeded = second.get(20, TimeUnit.SECONDS);

            int successCount =
                    (firstSucceeded ? 1 : 0) + (secondSucceeded ? 1 : 0);

            assertEquals(1, successCount, "송금은 하나만 성공해야 합니다.");

            assertEquals(30000L, balanceOf(fromId));
            assertEquals(70000L, balanceOf(toId));

            // 성공한 요청의 키만 DB에 남아야 한다.
            assertEquals(
                    firstSucceeded,
                    recordRepository.existsById(firstKey)
            );
            assertEquals(
                    secondSucceeded,
                    recordRepository.existsById(secondKey)
            );
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private boolean attemptTransfer(
            String requestKey,
            TransferRequest request
    ) {
        try {
            transferService.transfer(requestKey, request);
            return true;
        } catch (ResponseStatusException exception) {
            assertEquals(
                    HttpStatus.BAD_REQUEST,
                    exception.getStatusCode()
            );
            assertEquals(
                    "잔액이 부족합니다.",
                    exception.getReason()
            );

            return false;
        }
    }

    private long balanceOf(Long walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow()
                .getBalance();
    }
}