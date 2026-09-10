package com.example.equipment.model;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 器材。库存用 AtomicInteger，保证并发借用时不超卖。
 */
public class Equipment {

    private final int id;
    private final String name;
    private final AtomicInteger stock;

    public Equipment(int id, String name, int stock) {
        this.id = id;
        this.name = name;
        this.stock = new AtomicInteger(stock);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getStock() {
        return stock.get();
    }

    /**
     * 原子扣减库存。
     *
     * @param qty 借用数量
     * @return true=扣减成功；false=库存不足
     */
    public boolean borrow(int qty) {
        while (true) {
            int current = stock.get();
            if (current < qty) {
                return false;
            }
            // CAS：只有库存没被别的线程改掉时才提交，失败则自旋重试
            if (stock.compareAndSet(current, current - qty)) {
                return true;
            }
        }
    }

    /** 序列化为 JSON 片段 */
    public String toJson() {
        return "{\"id\":" + id
                + ",\"name\":" + com.example.equipment.util.Json.quote(name)
                + ",\"stock\":" + stock.get() + "}";
    }
}
