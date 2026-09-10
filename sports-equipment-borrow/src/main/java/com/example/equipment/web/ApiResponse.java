package com.example.equipment.web;

import com.example.equipment.util.Json;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * 统一 JSON 响应输出，所有响应显式声明 UTF-8，保证中文不乱码。
 */
final class ApiResponse {

    private ApiResponse() {
    }

    static void write(HttpServletResponse resp, int status, String json) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();
        out.print(json);
        out.flush();
    }

    static String body(int code, String message, Object dataJson) {
        String data = dataJson == null ? "null" : dataJson.toString();
        return "{\"code\":" + code
                + ",\"message\":" + Json.quote(message)
                + ",\"data\":" + data + "}";
    }
}
