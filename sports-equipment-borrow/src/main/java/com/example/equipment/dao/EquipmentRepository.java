package com.example.equipment.dao;

import com.example.equipment.model.Equipment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内存版器材仓库（演示用；生产应换数据库 + 行锁/乐观锁）。
 */
public class EquipmentRepository {

    private final Map<Integer, Equipment> store = new LinkedHashMap<>();

    /** 应用级共享仓库，保证“列表查询”和“借用扣减”看到的是同一份库存 */
    private static volatile EquipmentRepository instance;

    public static EquipmentRepository getInstance() {
        if (instance == null) {
            synchronized (EquipmentRepository.class) {
                if (instance == null) {
                    instance = defaultData();
                }
            }
        }
        return instance;
    }

    public static EquipmentRepository defaultData() {
        EquipmentRepository repo = new EquipmentRepository();
        repo.add(new Equipment(1, "篮球", 10));
        repo.add(new Equipment(2, "羽毛球拍", 8));
        repo.add(new Equipment(3, "跳绳", 15));
        return repo;
    }

    private void add(Equipment e) {
        store.put(e.getId(), e);
    }

    public Equipment findById(int id) {
        return store.get(id);
    }

    public List<Equipment> findAll() {
        List<Equipment> list = new ArrayList<>(store.values());
        Collections.sort(list, Comparator.comparingInt(Equipment::getId));
        return list;
    }
}
