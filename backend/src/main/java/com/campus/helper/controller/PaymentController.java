package com.campus.helper.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.helper.common.PageQuery;
import com.campus.helper.common.Result;
import com.campus.helper.entity.Payment;
import com.campus.helper.mapper.PaymentMapper;
import com.campus.helper.service.PaymentService;
import com.campus.helper.service.PaymentCallbackVerifier;
import com.campus.helper.security.AuthenticatedUser;
import com.campus.helper.security.SecurityUtils;
import com.campus.helper.common.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;
    private final PaymentCallbackVerifier callbackVerifier;

    public PaymentController(PaymentService paymentService,
                             PaymentMapper paymentMapper,
                             PaymentCallbackVerifier callbackVerifier) {
        this.paymentService = paymentService;
        this.paymentMapper = paymentMapper;
        this.callbackVerifier = callbackVerifier;
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
        Page<Payment> result = paymentMapper.selectPage(PageQuery.of(page, size), wrapper);
        return Result.success(result);
    }

    @PostMapping
    public Result createPayment(@RequestBody Map<String, Object> body, Authentication authentication) {
        AuthenticatedUser user = SecurityUtils.current(authentication);
        Long orderId = body.get("orderId") != null
                ? ((Number) body.get("orderId")).longValue() : null;
        String payChannel = (String) body.get("payChannel");
        Payment payment = paymentService.createPayment(orderId, payChannel, user.userId());
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
    public Result handleCallback(@RequestBody Map<String, Object> body,
                                 @RequestHeader(value = "X-Payment-Signature", required = false) String signature) {
        String paymentNo = (String) body.get("paymentNo");
        String transactionId = (String) body.get("transactionId");
        Boolean success = (Boolean) body.get("success");
        Long timestamp = body.get("timestamp") != null
                ? ((Number) body.get("timestamp")).longValue() : null;
        if (success == null
                || !callbackVerifier.verify(paymentNo, transactionId, success, timestamp, signature)) {
            throw new BusinessException(401, "支付回调签名无效");
        }
        paymentService.handleCallback(paymentNo, transactionId, success);
        return Result.success();
    }
}
