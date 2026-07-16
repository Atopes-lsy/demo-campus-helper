package com.campus.helper.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.helper.common.Result;
import com.campus.helper.entity.Payment;
import com.campus.helper.mapper.PaymentMapper;
import com.campus.helper.service.PaymentService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;

    public PaymentController(PaymentService paymentService,
                             PaymentMapper paymentMapper) {
        this.paymentService = paymentService;
        this.paymentMapper = paymentMapper;
    }

    /**
     * Paginated list of payments — for admin transaction view.
     */
    @GetMapping
    public Result list(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(required = false) Integer payStatus) {
        LambdaQueryWrapper<Payment> wrapper = new LambdaQueryWrapper<>();
        if (payStatus != null) {
            wrapper.eq(Payment::getPayStatus, payStatus);
        }
        wrapper.orderByDesc(Payment::getCreatedAt);
        Page<Payment> result = paymentMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.success(result);
    }

    @PostMapping
    public Result createPayment(@RequestBody Map<String, Object> body) {
        Long orderId = body.get("orderId") != null
                ? ((Number) body.get("orderId")).longValue() : null;
        String payChannel = (String) body.get("payChannel");
        Payment payment = paymentService.createPayment(orderId, payChannel);
        return Result.success(payment);
    }

    @GetMapping("/{paymentNo}")
    public Result queryByNo(@PathVariable String paymentNo) {
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>()
                        .eq(Payment::getPaymentNo, paymentNo)
        );
        return Result.success(payment);
    }

    @PostMapping("/callback")
    public Result handleCallback(@RequestBody Map<String, Object> body) {
        String paymentNo = (String) body.get("paymentNo");
        String transactionId = (String) body.get("transactionId");
        Boolean success = (Boolean) body.get("success");
        // TODO: update payment status based on callback result
        return Result.success();
    }
}
