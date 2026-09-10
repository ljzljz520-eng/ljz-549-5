package com.example.equipment.service;

import com.example.equipment.dao.EquipmentRepository;
import com.example.equipment.model.BorrowResult;
import com.example.equipment.model.Equipment;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 借用业务：参数校验 + 原子扣库存 + 幂等表（解决超时重试 / 连点重复提交）。
 */
public class BorrowService {

    /** 前端幂等键格式：UUID，8-64 位字母数字/横线 */
    private static final String REQUEST_ID_PATTERN = "[A-Za-z0-9-]{8,64}";
    private static final int MAX_QTY = 100;
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final EquipmentRepository repository;
    private final Map<String, BorrowResult> idempotencyCache = new ConcurrentHashMap<>();

    public BorrowService(EquipmentRepository repository) {
        this.repository = repository;
    }

    /**
     * @param requestId 幂等键，可为 null（为空时不做幂等）
     */
    public BorrowResult borrow(String requestId, String equipmentIdRaw,
                               String quantityRaw, String returnTimeRaw) {
        // 0. 幂等：同一 requestId 直接回放上次的成功结果，绝不重复扣库存
        if (requestId != null && !requestId.isEmpty()) {
            if (!requestId.matches(REQUEST_ID_PATTERN)) {
                return BorrowResult.fail(BorrowResult.BAD_REQUEST, "请求标识格式错误", null);
            }
            BorrowResult cached = idempotencyCache.get(requestId);
            if (cached != null) {
                return cached.asReplay();
            }
        }

        // 1. 参数校验 —— 器材 ID
        int equipmentId;
        try {
            equipmentId = Integer.parseInt(equipmentIdRaw.trim());
        } catch (RuntimeException e) {
            return fail(requestId, "器材不存在或参数错误");
        }
        Equipment equipment = repository.findById(equipmentId);
        if (equipment == null) {
            return fail(requestId, "器材不存在");
        }

        // 2. 参数校验 —— 数量
        int quantity;
        try {
            quantity = Integer.parseInt(quantityRaw.trim());
        } catch (RuntimeException e) {
            return fail(requestId, "借用数量必须是整数");
        }
        if (quantity <= 0) {
            return fail(requestId, "借用数量必须大于 0");
        }
        if (quantity > MAX_QTY) {
            return fail(requestId, "单次借用数量不能超过 " + MAX_QTY);
        }

        // 3. 参数校验 —— 归还时间（yyyy-MM-ddTHH:mm）
        LocalDateTime returnTime;
        try {
            returnTime = parseLocalDateTime(returnTimeRaw.trim());
        } catch (DateTimeParseException e) {
            return fail(requestId, "归还时间格式错误，应为 yyyy-MM-ddTHH:mm");
        }
        if (returnTime.isBefore(LocalDateTime.now().minusMinutes(1))) {
            return fail(requestId, "归还时间不能早于当前时间");
        }

        // 4. 原子扣减库存（高并发连点不超卖）
        if (!equipment.borrow(quantity)) {
            return BorrowResult.fail(BorrowResult.CONFLICT,
                    "库存不足：「" + equipment.getName() + "」当前仅剩 " + equipment.getStock() + " 件",
                    requestId);
        }

        BorrowResult result = BorrowResult.ok(equipment.getName(), quantity,
                equipment.getStock(), requestId);
        cacheSuccess(requestId, result);
        return result;
    }

    /**
     * 只缓存成功结果：失败请求（如库存不足）放行重试，
     * 等库存恢复或参数修正后仍可成功。缓存加简单上限防止内存膨胀。
     */
    private void cacheSuccess(String requestId, BorrowResult result) {
        if (requestId != null && !requestId.isEmpty()) {
            if (idempotencyCache.size() < 10000) {
                idempotencyCache.put(requestId, result);
            }
        }
    }

    /**
     * 兼容两种入参：
     *   yyyy-MM-ddTHH:mm（datetime-local 控件）
     *   yyyy-MM-ddTHH:mm:ss（curl 等手动调用）
     */
    private LocalDateTime parseLocalDateTime(String raw) {
        if (raw.length() == 16) {
            return LocalDateTime.parse(raw + ":00", ISO);
        }
        return LocalDateTime.parse(raw, ISO);
    }

    private BorrowResult fail(String requestId, String message) {
        return BorrowResult.fail(BorrowResult.BAD_REQUEST, message, requestId);
    }
}
