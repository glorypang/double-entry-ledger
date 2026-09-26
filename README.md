# 복식부기 원장 시스템 (Double-Entry Ledger System)

## 프로젝트 개요

금융 시스템의 거래 기록과 잔액 정합성을 학습하기 위한 가상 지갑 프로젝트입니다. 잔액만 변경하는 송금 기능에서 시작해 **복식부기 원장**, **동시성 제어**, **멱등성**을 구현했습니다.

### 핵심 기술

- **Double-Entry Bookkeeping**: 거래별 차변·대변 기록
- **Transaction Management**: 잔액 변경과 원장 저장의 원자성 보장
- **Concurrency Control**: 비관적 잠금으로 동시 출금 제어
- **Idempotency**: 요청 키와 DB 유일성 제약으로 중복 송금 방지

---

## 아키텍처

### 송금 처리 구조

```text
Client
  │ POST /transfers + Idempotency-Key
  ▼
TransferController
  ▼
IdempotentTransferService ── 요청 중복 확인
  │
  └─ 하나의 DB 트랜잭션
       ├─ 요청 키 저장
       ├─ TransferService
       │    ├─ 지갑 잠금 및 잔액 검증
       │    ├─ 출금·입금
       │    └─ 거래 및 차변·대변 저장
       └─ 전체 커밋 또는 롤백
                │
                ▼
              MySQL
```

### 데이터 구성

| 테이블 | 역할 |
|---|---|
| `wallet` | 고객 지갑 및 누적 잔액 |
| `journal_transaction` | 충전·송금 거래 단위 |
| `journal_entry` | 거래별 차변·대변 기록 |
| `transfer_request_record` | 성공한 송금의 요청 키와 요청 내용 |

거래 하나에 여러 원장 기록이 연결됩니다. 계정은 `account_type`으로 회사 현금(`CASH`)과 고객 지갑(`WALLET`)을 구분하며, 고객 지갑은 `wallet_id`로 식별합니다.

---

## 복식부기 원장

고객 지갑은 **운영자가 고객에게 돌려줘야 하는 부채**로 모델링했습니다.

### 충전: A 지갑에 100,000원

| 계정 | 차변 | 대변 |
|---|---:|---:|
| 회사 현금 자산 | 100,000 | 0 |
| A 지갑 부채 | 0 | 100,000 |

### 송금: A → B, 30,000원

| 계정 | 차변 | 대변 |
|---|---:|---:|
| A 지갑 부채 | 30,000 | 0 |
| B 지갑 부채 | 0 | 30,000 |

```text
거래별 차변 합계 = 대변 합계
고객 지갑 잔액 = 대변 합계 − 차변 합계
```

잔액 조회에는 `wallet.balance`를 사용하고, 검증 시 원장을 합산해 저장된 잔액과 비교합니다. 현재 균형은 서비스의 기록 생성 로직으로 유지하며, DB에서 여러 원장 행의 합계를 강제하지는 않습니다.

---

## 정합성 제어

### 1. 중간 장애 시 롤백

`@Transactional`과 `TransactionTemplate`을 사용해 요청 기록, 잔액 변경, 원장 저장을 하나의 트랜잭션으로 묶었습니다.

```text
출금 → 입금 → 거래·원장 저장 → 커밋
              ↓ 처리 중 예외
           전체 롤백
```

### 2. 동시 송금 제어

- `PESSIMISTIC_WRITE`로 잔액 변경 전 지갑 잠금
- 잠금 획득 후 잔액 부족 여부 확인
- 송금과 충전에 동일한 잠금 규칙 적용
- 지갑 ID가 작은 순서로 잠가 교착 상태 가능성 감소

```text
A 잔액 100,000원
  ├─ 송금 1: 70,000원 → 성공
  └─ 송금 2: 70,000원 → 잠금 대기 → 잔액 부족
```

### 3. 중복 송금 방지

송금 요청의 `Idempotency-Key`를 DB 기본키로 저장합니다. 키 저장과 송금이 함께 커밋되므로 실패한 요청의 키는 롤백됩니다.

| 요청 | 처리 |
|---|---|
| 새 키 | 송금 실행 |
| 같은 키 + 같은 내용 | 추가 송금 없이 204 반환 |
| 같은 키 + 다른 내용 | 409 반환 |
| 실패한 요청 재시도 | 다시 처리 가능 |

동시에 같은 키를 저장하다 충돌하면, 실패한 트랜잭션 종료 후 기존 요청을 조회해 내용을 비교합니다.

---

## 빠른 시작

### 1. 환경 요구사항

- Java 21
- MySQL
- 프로젝트에 포함된 Gradle Wrapper

### 2. 데이터베이스 생성

```sql
CREATE DATABASE ledger CHARACTER SET utf8mb4;
CREATE DATABASE ledger_test CHARACTER SET utf8mb4;
```

### 3. 환경변수 설정

애플리케이션과 테스트 실행 환경에 각각 설정합니다.

```text
DB_USERNAME=본인의MySQL계정
DB_PASSWORD=본인의MySQL비밀번호
```

`application.yml`의 연결 설정:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ledger
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: update
```

`ddl-auto: update`는 로컬 학습용으로 사용합니다. 테스트는 `application-test.yml`에서 DB를 `ledger_test`로 지정합니다.

### 4. 실행

프로젝트 루트의 PowerShell에서 실행합니다.

```powershell
.\gradlew.bat bootRun
```

기본 주소: `http://localhost:8080`

---

## API

| 메서드 | 경로 | 기능 | 성공 응답 |
|---|---|---|---|
| POST | `/wallets` | 지갑 생성 | 201 |
| GET | `/wallets/{id}` | 지갑 조회 | 200 |
| POST | `/wallets/{id}/deposits` | 가상 충전 | 204 |
| POST | `/transfers` | 송금 | 204 |

### 지갑 생성

```http
POST /wallets
Content-Type: application/json

{
  "ownerName": "A"
}
```

### 충전

```http
POST /wallets/1/deposits
Content-Type: application/json

{
  "amount": 100000
}
```

### 송금

```http
POST /transfers
Content-Type: application/json
Idempotency-Key: transfer-example-001

{
  "fromWalletId": 1,
  "toWalletId": 2,
  "amount": 30000
}
```

지갑 ID는 실제 생성 결과로 변경합니다. 별개의 송금에는 새 키를 사용하며, 키는 영문·숫자·하이픈·밑줄로 구성된 1~128자 문자열입니다.

---

## 테스트

실제 MySQL을 사용하는 서비스 통합 테스트입니다. 테스트마다 새로운 지갑과 요청 키를 생성하며, 데이터는 테스트 DB에 남습니다.

```powershell
.\gradlew.bat test
```

### 검증 시나리오

| 시나리오 | 기대 결과 |
|---|---|
| 동일 요청 재전송 | 송금 한 번만 반영 |
| 같은 키로 금액 변경 | 409, 추가 잔액 변경 없음 |
| 잔액 부족 후 충전·재시도 | 실패한 키 롤백 및 재시도 성공 |
| 동일 요청 동시 전송 | 실제 송금 한 건 |
| 서로 다른 요청의 잔액 초과 동시 송금 | 한 건 성공, 한 건 실패 |
| 원장과 잔액 비교 | 저장된 잔액과 원장 집계 일치 |
| 거래 균형 및 건수 확인 | 차변·대변 일치, 성공한 송금만 기록 |

위 표는 검증 시나리오입니다. 최신 통과 여부는 실행 결과로 확인하며, 동시성 테스트는 시작 시점을 맞추되 특정 충돌 순서까지 강제하지는 않습니다.

---

## 프로젝트 구조

주요 구현 파일 기준입니다.

```text
com.example.ledger
├── DoubleEntryBookkeepingApplication.java
├── wallet
│   ├── Wallet.java
│   ├── WalletRepository.java
│   ├── WalletController.java
│   ├── CreateWalletRequest.java
│   ├── WalletResponse.java
│   ├── DepositRequest.java
│   ├── DepositService.java
│   └── DepositController.java
├── transfer
│   ├── TransferRequest.java
│   ├── TransferController.java
│   ├── TransferService.java
│   ├── IdempotentTransferService.java
│   ├── TransferRequestRecord.java
│   └── TransferRequestRecordRepository.java
└── ledger
    ├── AccountType.java
    ├── EntryType.java
    ├── TransactionType.java
    ├── JournalTransaction.java
    ├── JournalTransactionRepository.java
    ├── LedgerEntry.java
    └── LedgerEntryRepository.java
```

---

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot |
| Database | MySQL |
| ORM | Spring Data JPA / Hibernate |
| Validation | Jakarta Validation |
| Test | JUnit 5 / Spring Boot Test |
| Build | Gradle |

---

## 핵심 학습 포인트

- **트랜잭션과 잠금의 차이**: 원자성 보장과 동시 접근 제어는 별도로 설계해야 합니다.
- **잠금과 멱등성의 차이**: 순서대로 처리해도 같은 요청이 반복되면 중복 송금이 발생할 수 있습니다.
- **잔액과 원장의 역할**: 잔액은 현재 상태를, 원장은 금액 변화의 근거를 나타냅니다.
- **복식부기의 한계**: 차변·대변 균형만으로 중복 거래나 잘못된 수취인을 검출할 수는 없습니다.
- **금액의 정밀도**: 원화 정수 금액은 `long`으로 관리하고 입금 시 `Math.addExact`로 오버플로를 검사합니다.
