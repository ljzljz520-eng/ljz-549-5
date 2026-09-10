package com.example.equipment.web;

import com.example.equipment.dao.EquipmentRepository;
import com.example.equipment.model.BorrowResult;
import com.example.equipment.service.BorrowService;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * POST /api/borrow —— 提交借用申请。
 *
 * 表单参数（application/x-www-form-urlencoded，UTF-8）：
 *   equipmentId 器材 ID（1 篮球 / 2 羽毛球拍 / 3 跳绳）
 *   quantity    数量
 *   returnTime  归还时间，yyyy-MM-ddTHH:mm
 *
 * 可选请求头：
 *   X-Request-Id 幂等键（前端每次打开表单生成一个，超时重试复用，成功后换新）
 *
 * 响应：200 成功 / 400 参数错误 / 405 方法不允许 / 409 库存不足
 */
@WebServlet("/api/borrow")
public class BorrowServlet extends HttpServlet {

    private final BorrowService borrowService = new BorrowService(EquipmentRepository.getInstance());

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String requestId = req.getHeader("X-Request-Id");
        if (requestId != null) {
            requestId = requestId.trim();
            if (requestId.isEmpty()) {
                requestId = null;
            }
        }

        String equipmentId = req.getParameter("equipmentId");
        String quantity = req.getParameter("quantity");
        String returnTime = req.getParameter("returnTime");

        BorrowResult result = borrowService.borrow(requestId,
                equipmentId == null ? "" : equipmentId,
                quantity == null ? "" : quantity,
                returnTime == null ? "" : returnTime);

        // HTTP 状态码与业务 code 保持一致，前端可直接按状态/消息提示
        ApiResponse.write(resp, result.getCode(), result.toJson());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ApiResponse.write(resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                ApiResponse.body(405, "请使用 POST 提交借用申请", null));
    }
}
