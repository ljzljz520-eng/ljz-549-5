# 体育器材借用申请示例

Java Servlet + 原生 HTML/JS 实现的器材借用 Demo。学生从下拉框选择 **篮球 / 羽毛球拍 / 跳绳**，
填写数量与归还时间后提交；后端返回「成功 / 参数错误 / 库存不足」。

## 技术栈

- 后端：Servlet 4.0（注解配置）、Java 8，无第三方 JSON 依赖
- 前端：原生 HTML/CSS/JavaScript（fetch + AbortController）
- 构建：Maven（war 包），内置 Jetty 插件，免装容器直接运行

## 运行

```bash
cd sports-equipment-borrow
mvn jetty:run
# 浏览器打开 http://localhost:8080/
```

运行测试：

```bash
mvn test
```

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET  | `/api/equipments` | 器材列表 |
| POST | `/api/borrow` | 提交借用申请 |

POST 参数（`application/x-www-form-urlencoded`，UTF-8）：

| 参数 | 说明 |
| --- | --- |
| `equipmentId` | 器材 ID：1 篮球 / 2 羽毛球拍 / 3 跳绳 |
| `quantity` | 数量，1~100 的整数 |
| `returnTime` | 归还时间，`yyyy-MM-ddTHH:mm` |

请求头 `X-Request-Id`：幂等键（可选）。

响应示例：

```json
// 200 成功
{"code":200,"message":"借用成功","data":{"equipmentName":"篮球","quantity":2,"remaining":8},"requestId":"..."}

// 400 参数错误
{"code":400,"message":"借用数量必须大于 0","data":null,"requestId":"..."}

// 409 库存不足
{"code":409,"message":"库存不足：「篮球」当前仅剩 1 件","data":null,"requestId":"..."}
```

curl 验证（注意中文参数需 --data-urlencode）：

```bash
curl 'http://localhost:8080/api/equipments'
curl -X POST 'http://localhost:8080/api/borrow' \
  -H 'X-Request-Id: demo-001' \
  --data-urlencode 'equipmentId=1' \
  --data-urlencode 'quantity=2' \
  --data-urlencode 'returnTime=2026-09-20T18:00'
# 同一 X-Request-Id 再发一次 -> 回放首次成功结果（data.replay=true），库存不再扣减
```

## 关键问题的处理

### 1. 按钮连点 / 重复提交

- 前端：`submitting` 标志 + 按钮禁用（置灰显示「提交中…」），进行中的点击直接忽略。
- 后端：`X-Request-Id` 幂等表（`ConcurrentHashMap`），同一申请只扣一次库存；
  重复提交回放原结果并带 `data.replay=true`。
- 库存扣减：`AtomicInteger` CAS 自旋，20 人抢 10 个篮球时只成功 10 个，绝不超卖（见并发测试）。
- 只缓存**成功**结果：库存不足 / 参数错误不放行缓存，修正后可继续申请。

### 2. 中文参数

- 表单声明 `Content-Type: application/x-www-form-urlencoded;charset=UTF-8`，
  前端用 `encodeURIComponent` 编码。
- 后端 `EncodingFilter` 在**任何 `getParameter` 调用之前**设置
  `request.setCharacterEncoding("UTF-8")`（配在 web.xml 的 `/api/*`）。
- 响应统一 `application/json;charset=UTF-8`；JSON 序列化不转义中文，只转义引号/控制字符。
- 页面消息使用 `textContent` 输出，避免 XSS。

### 3. 请求超时

- 前端 8 秒超时（`AbortController` 中止连接）；列表加载、提交均生效。
- 超时提示明确告知「服务端可能已处理」，重试**复用同一个 `X-Request-Id`**：
  若服务端已成功则幂等回放，未成功则正常借用，不会出现“以为失败结果借了两次”。
- 成功或收到明确业务失败（4xx）后才更换 requestId。

## 说明

库存与幂等表保存在内存中，重启即恢复初始值（篮球 10、羽毛球拍 8、跳绳 15），
生产环境应替换为数据库事务（`UPDATE ... SET stock = stock - ? WHERE stock >= ?`）或分布式幂等存储。
