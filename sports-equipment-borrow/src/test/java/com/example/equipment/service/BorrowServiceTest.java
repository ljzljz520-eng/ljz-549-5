package com.example.equipment.service;

import com.example.equipment.dao.EquipmentRepository;
import com.example.equipment.model.BorrowResult;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BorrowServiceTest {

    private EquipmentRepository repository;
    private BorrowService service;
    private String futureTime;

    @Before
    public void setUp() {
        repository = EquipmentRepository.defaultData();
        service = new BorrowService(repository);
        futureTime = LocalDateTime.now().plusDays(3).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
    }

    @Test
    public void borrowSuccess() {
        BorrowResult r = service.borrow("req-0001-aaaa", "1", "2", futureTime);
        assertEquals(BorrowResult.OK, r.getCode());
        assertEquals(8, repository.findById(1).getStock());
    }

    @Test
    public void equipmentNotFound() {
        BorrowResult r = service.borrow(null, "999", "1", futureTime);
        assertEquals(BorrowResult.BAD_REQUEST, r.getCode());
        assertTrue(r.toJson().contains("器材不存在"));
    }

    @Test
    public void invalidQuantityZero() {
        assertEquals(BorrowResult.BAD_REQUEST, service.borrow(null, "1", "0", futureTime).getCode());
    }

    @Test
    public void invalidQuantityText() {
        assertEquals(BorrowResult.BAD_REQUEST, service.borrow(null, "1", "两个", futureTime).getCode());
    }

    @Test
    public void pastReturnTimeRejected() {
        String past = LocalDateTime.now().minusHours(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
        assertEquals(BorrowResult.BAD_REQUEST, service.borrow(null, "1", "1", past).getCode());
    }

    @Test
    public void badTimeFormatRejected() {
        assertEquals(BorrowResult.BAD_REQUEST, service.borrow(null, "1", "1", "2026/09/20 10:00").getCode());
    }

    @Test
    public void insufficientStock() {
        BorrowResult r = service.borrow(null, "1", "100", futureTime);
        assertEquals(BorrowResult.CONFLICT, r.getCode());
        assertEquals(10, repository.findById(1).getStock());
        assertTrue(r.toJson().contains("库存不足"));
    }

    @Test
    public void idempotentSameRequestIdBorrowsOnce() {
        BorrowResult first = service.borrow("req-fixed-id-1", "2", "1", futureTime);
        assertEquals(BorrowResult.OK, first.getCode());
        BorrowResult second = service.borrow("req-fixed-id-1", "2", "1", futureTime);
        // 第二次为幂等回放，库存只扣一次
        assertEquals(BorrowResult.OK, second.getCode());
        assertTrue(second.toJson().contains("\"replay\":true"));
        assertEquals(7, repository.findById(2).getStock());
    }

    @Test
    public void concurrentBorrowNeverOversells() throws InterruptedException {
        // 篮球初始 10 件，20 个线程同时各借 1 件，只允许 10 个成功
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    BorrowResult r = service.borrow(null, "1", "1", futureTime);
                    if (r.getCode() == BorrowResult.OK) {
                        success.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        assertEquals(10, success.get());
        assertEquals(0, repository.findById(1).getStock());
    }
}
