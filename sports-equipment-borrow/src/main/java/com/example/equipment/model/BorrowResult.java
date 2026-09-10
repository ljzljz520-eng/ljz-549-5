package com.example.equipment.model;

import com.example.equipment.util.Json;

/**
 * 借用结果。HTTP 状态码与业务 code 对齐：
 * 200 成功 / 400 参数错误 / 409 库存不足。
 */
public class BorrowResult {

    public static final int OK = 200;
    public static final int BAD_REQUEST = 400;
    public static final int CONFLICT = 409;

    private final int code;
    private final String message;
    private final String equipmentName;
    private final Integer quantity;
    private final Integer remaining;
    private final String requestId;
    private final boolean replay;

    private BorrowResult(int code, String message, String equipmentName, Integer quantity,
                         Integer remaining, String requestId, boolean replay) {
        this.code = code;
        this.message = message;
        this.equipmentName = equipmentName;
        this.quantity = quantity;
        this.remaining = remaining;
        this.requestId = requestId;
        this.replay = replay;
    }

    public static BorrowResult ok(String name, int qty, int remaining, String requestId) {
        return new BorrowResult(OK, "借用成功", name, qty, remaining, requestId, false);
    }

    public static BorrowResult fail(int code, String message, String requestId) {
        return new BorrowResult(code, message, null, null, null, requestId, false);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    /** 幂等命中：返回同一成功结果，但标记 replay=true，不再扣库存 */
    public BorrowResult asReplay() {
        return new BorrowResult(code, message, equipmentName, quantity, remaining, requestId, true);
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"code\":").append(code);
        sb.append(",\"message\":").append(Json.quote(message));
        sb.append(",\"data\":");
        if (equipmentName == null) {
            sb.append("null");
        } else {
            sb.append("{");
            sb.append("\"equipmentName\":").append(Json.quote(equipmentName));
            sb.append(",\"quantity\":").append(quantity);
            sb.append(",\"remaining\":").append(remaining);
            if (replay) {
                sb.append(",\"replay\":true");
            }
            sb.append("}");
        }
        if (requestId != null) {
            sb.append(",\"requestId\":").append(Json.quote(requestId));
        }
        sb.append("}");
        return sb.toString();
    }
}
