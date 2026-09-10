(function () {
    'use strict';

    var TIMEOUT_MS = 8000; // 请求超时：8 秒

    var form = document.getElementById('borrow-form');
    var equipmentSelect = document.getElementById('equipment');
    var quantityInput = document.getElementById('quantity');
    var returnTimeInput = document.getElementById('return-time');
    var submitBtn = document.getElementById('submit-btn');
    var messageBox = document.getElementById('message');
    var stockHint = document.getElementById('stock-hint');
    var retryLoadBtn = document.getElementById('retry-load');

    var equipments = [];
    var submitting = false;   // 防连点：进行中标志
    var currentController = null;

    /**
     * 幂等键：每次“开始一笔新申请”时生成。
     * 超时后点“重试”复用同一个键，服务端据此去重，绝不重复扣库存；
     * 成功（或收到明确的 4xx 业务失败）后换新键。
     */
    var requestId = generateRequestId();

    // ---------- 工具 ----------

    function generateRequestId() {
        if (window.crypto && crypto.randomUUID) {
            return crypto.randomUUID();
        }
        return 'id-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 12);
    }

    function showMessage(type, text) {
        messageBox.hidden = false;
        messageBox.className = 'message ' + type;
        messageBox.textContent = text; // textContent，避免返回内容引发 XSS
    }

    function clearMessage() {
        messageBox.hidden = true;
        messageBox.textContent = '';
    }

    /**
     * 带超时的 fetch。超时后底层请求被中止，Promise reject。
     * 注意：超时只代表“客户端没等到响应”，服务端可能已成功，
     * 因此重试必须携带同一 X-Request-Id，由服务端幂等返回原结果。
     */
    function fetchWithTimeout(url, options, timeoutMs) {
        var controller = new AbortController();
        var timer = setTimeout(function () { controller.abort(); }, timeoutMs);
        currentController = controller;
        options.signal = controller.signal;
        return fetch(url, options).then(
            function (resp) {
                clearTimeout(timer);
                return resp;
            },
            function (err) {
                clearTimeout(timer);
                throw err;
            }
        );
    }

    function pad(n) { return n < 10 ? '0' + n : '' + n; }

    // datetime-local 控件值格式：yyyy-MM-ddTHH:mm
    function toLocalInputValue(date) {
        return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
            + 'T' + pad(date.getHours()) + ':' + pad(date.getMinutes());
    }

    // ---------- 器材列表 ----------

    function loadEquipments() {
        retryLoadBtn.hidden = true;
        equipmentSelect.innerHTML = '<option value="">器材加载中…</option>';
        submitBtn.disabled = true;

        fetchWithTimeout('api/equipments', { method: 'GET' }, TIMEOUT_MS)
            .then(function (resp) {
                if (!resp.ok) {
                    throw new Error('HTTP ' + resp.status);
                }
                return resp.json();
            })
            .then(function (body) {
                equipments = body.data || [];
                renderOptions();
                submitBtn.disabled = equipments.length === 0;
                if (equipments.length === 0) {
                    showMessage('warning', '暂无可借器材');
                }
            })
            .catch(function (err) {
                equipmentSelect.innerHTML = '<option value="">器材列表加载失败</option>';
                retryLoadBtn.hidden = false;
                var tip = err.name === 'AbortError'
                    ? '器材列表加载超时，请检查网络后重试'
                    : '器材列表加载失败，请重试';
                showMessage('error', tip);
            });
    }

    function renderOptions() {
        var html = '<option value="">请选择器材</option>';
        equipments.forEach(function (e) {
            html += '<option value="' + e.id + '">' + e.name + '（库存 ' + e.stock + '）</option>';
        });
        equipmentSelect.innerHTML = html;
        updateStockHint();
    }

    function updateStockHint() {
        var selected = getSelectedEquipment();
        if (!selected) {
            stockHint.textContent = '';
            stockHint.classList.remove('low');
            return;
        }
        stockHint.textContent = '当前可借：' + selected.stock + ' 件';
        stockHint.classList.toggle('low', selected.stock <= 3);
    }

    function getSelectedEquipment() {
        var id = equipmentSelect.value;
        if (!id) return null;
        for (var i = 0; i < equipments.length; i++) {
            if (String(equipments[i].id) === String(id)) return equipments[i];
        }
        return null;
    }

    // ---------- 提交借用 ----------

    form.addEventListener('submit', function (event) {
        event.preventDefault();

        // 防连点：上一笔仍在进行中则直接忽略
        if (submitting) {
            return;
        }

        // 客户端基础校验（服务端仍会完整再校验一遍）
        var equipment = getSelectedEquipment();
        var quantity = parseInt(quantityInput.value, 10);
        var returnTime = returnTimeInput.value;

        if (!equipment) {
            showMessage('error', '请选择器材');
            return;
        }
        if (!quantityInput.value || isNaN(quantity) || quantity <= 0) {
            showMessage('error', '请输入大于 0 的借用数量');
            return;
        }
        if (quantity > equipment.stock) {
            showMessage('error', '借用数量不能超过当前库存（' + equipment.stock + ' 件）');
            return;
        }
        if (!returnTime) {
            showMessage('error', '请选择归还时间');
            return;
        }
        if (returnTime < returnTimeInput.min) {
            showMessage('error', '归还时间不能早于当前时间');
            return;
        }

        clearMessage();
        setSubmitting(true);

        // encodeURIComponent 保证中文/特殊字符按 UTF-8 正确传输
        var body = 'equipmentId=' + encodeURIComponent(equipment.id)
            + '&quantity=' + encodeURIComponent(quantity)
            + '&returnTime=' + encodeURIComponent(returnTime);

        fetchWithTimeout('api/borrow', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                'X-Request-Id': requestId // 同一笔申请（含超时重试）保持不变
            },
            body: body
        }, TIMEOUT_MS)
            .then(function (resp) {
                return resp.json().then(function (body) {
                    return { status: resp.status, body: body };
                });
            })
            .then(function (r) {
                handleResult(r.status, r.body);
            })
            .catch(function (err) {
                if (err.name === 'AbortError') {
                    // 超时：服务端可能已扣减，允许用同一 requestId 幂等重试
                    showMessage('warning',
                        '请求超时，服务端可能已处理。请点击“提交申请”查询/重试（不会重复借用）');
                } else {
                    showMessage('error', '网络异常，请稍后重试（同一申请不会重复提交）');
                }
            })
            .then(function () {
                setSubmitting(false);
            });
    });

    function handleResult(status, body) {
        var data = body && body.data;
        if (status === 200 && data) {
            var replayTip = data.replay ? '（该申请此前已提交，本次为幂等确认）' : '';
            showMessage('success',
                body.message + '：' + data.equipmentName + ' × ' + data.quantity
                + '，剩余库存 ' + data.remaining + ' 件' + replayTip);
            requestId = generateRequestId(); // 成功后换新键，开始下一笔
            quantityInput.value = '1';
            loadEquipments(); // 刷新库存
            return;
        }
        // 400 参数错误 / 409 库存不足：展示服务端中文消息
        var msg = (body && body.message) ? body.message : '提交失败（状态码 ' + status + '）';
        var type = status === 409 ? 'warning' : 'error';
        showMessage(type, msg);
        requestId = generateRequestId(); // 明确失败，换新键避免误幂等
        if (status === 409) {
            loadEquipments(); // 库存不足时刷新最新库存
        }
    }

    function setSubmitting(on) {
        submitting = on;
        submitBtn.disabled = on || equipments.length === 0;
        submitBtn.textContent = on ? '提交中…' : '提交申请';
    }

    equipmentSelect.addEventListener('change', updateStockHint);
    retryLoadBtn.addEventListener('click', loadEquipments);

    // ---------- 初始化 ----------

    (function init() {
        var now = new Date();
        returnTimeInput.min = toLocalInputValue(now);
        var threeDaysLater = new Date(now.getTime() + 3 * 24 * 60 * 60 * 1000);
        returnTimeInput.value = toLocalInputValue(threeDaysLater);
        loadEquipments();
    })();
})();
