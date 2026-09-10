package com.example.equipment.web;

import com.example.equipment.dao.EquipmentRepository;
import com.example.equipment.model.Equipment;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GET /api/equipments —— 返回器材列表（前端下拉框数据源）。
 */
@WebServlet("/api/equipments")
public class EquipmentListServlet extends HttpServlet {

    private final EquipmentRepository repository = EquipmentRepository.getInstance();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        List<Equipment> list = repository.findAll();
        String data = list.stream().map(Equipment::toJson).collect(Collectors.joining(","));
        ApiResponse.write(resp, 200, ApiResponse.body(200, "OK", "[" + data + "]"));
    }
}
