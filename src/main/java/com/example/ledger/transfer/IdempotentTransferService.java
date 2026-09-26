package com.example.ledger.transfer;

import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdempotentTransferService {

    private final TransferRequestRecordRepository recordRepository;
    private final TransferService transferService;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    public IdempotentTransferService(
            TransferRequestRecordRepository recordRepository,
            TransferService transferService,
            EntityManager entityManager,
            PlatformTransactionManager transactionManager
    ) {
        this.recordRepository = recordRepository;
        this.transferService = transferService;
        this.entityManager = entityManager;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
    }

    public void transfer(String key, TransferRequest request) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "요청 키는 영문, 숫자, 하이픈, 밑줄로 1~128자여야 합니다."
            );
        }

        var existing = recordRepository.findById(key);

        if (existing.isPresent()) {
            verifySameRequest(existing.get(), request);
            return;
        }

        try {
            transactionTemplate.executeWithoutResult(status -> {
                // 직접 지정한 ID를 기존 데이터와 병합하지 않고,
                // 반드시 새 행으로 삽입한다.
                entityManager.persist(
                        new TransferRequestRecord(key, request)
                );

                // 송금 전에 중복 키 충돌 여부를 확인한다.
                recordRepository.flush();

                // 기존 @Transactional 메서드가 이 트랜잭션에 참여한다.
                transferService.transfer(request);
            });
        } catch (DataIntegrityViolationException exception) {
            // 동시에 들어온 같은 키의 요청이 먼저 완료됐을 수 있다.
            // 실패한 트랜잭션이 끝난 뒤 기존 기록을 다시 조회한다.
            var completed = recordRepository.findById(key);

            if (completed.isEmpty()) {
                throw exception;
            }

            verifySameRequest(completed.get(), request);
        }
    }

    private void verifySameRequest(
            TransferRequestRecord existing,
            TransferRequest request
    ) {
        if (!existing.matches(request)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "같은 요청 키를 다른 송금 내용에 사용할 수 없습니다."
            );
        }
    }
}