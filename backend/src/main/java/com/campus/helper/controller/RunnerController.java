package com.campus.helper.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.helper.common.BusinessException;
import com.campus.helper.common.Result;
import com.campus.helper.entity.Runner;
import com.campus.helper.mapper.RunnerMapper;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/runners")
public class RunnerController {

    private final RunnerMapper runnerMapper;

    public RunnerController(RunnerMapper runnerMapper) {
        this.runnerMapper = runnerMapper;
    }

    /**
     * Paginated runner list with optional status filter.
     */
    @GetMapping
    public Result list(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Runner> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Runner::getStatus, status);
        }
        wrapper.orderByDesc(Runner::getCreatedAt);
        Page<Runner> result = runnerMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.success(result);
    }

    @GetMapping("/top")
    public Result getTop(@RequestParam(defaultValue = "5") int limit) {
        List<Map<String, Object>> list = runnerMapper.selectTopRunners(limit);
        return Result.success(list);
    }

    @GetMapping("/{id}")
    public Result getById(@PathVariable Long id) {
        Runner runner = runnerMapper.selectById(id);
        if (runner == null) {
            throw new BusinessException(404, "跑腿员不存在");
        }
        return Result.success(runner);
    }

    /**
     * Register a new runner.
     */
    @PostMapping
    public Result create(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String phone = (String) body.get("phone");
        String idCard = (String) body.get("idCard");

        if (name == null || name.isBlank()) {
            throw new BusinessException(400, "姓名不能为空");
        }
        if (phone == null || phone.isBlank()) {
            throw new BusinessException(400, "手机号不能为空");
        }

        // Check phone uniqueness
        Long phoneCount = runnerMapper.selectCount(
                new LambdaQueryWrapper<Runner>().eq(Runner::getPhone, phone));
        if (phoneCount > 0) {
            throw new BusinessException(400, "该手机号已被注册");
        }

        Runner runner = new Runner();
        runner.setRunnerNo("RN"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + ThreadLocalRandom.current().nextInt(1000, 10000));
        runner.setName(name);
        runner.setPhone(phone);
        runner.setIdCard(idCard != null ? idCard : "");

        // Password: use provided password, or default 123456
        String password = body.get("password") != null
                ? (String) body.get("password") : "123456";
        runner.setPassword(password);

        runner.setStatus(0);
        runner.setRating(new java.math.BigDecimal("5.0"));
        runner.setCompletedOrders(0);
        runner.setMaxConcurrent(3);

        runnerMapper.insert(runner);
        return Result.success(runner);
    }

    /**
     * Update runner info — admin only.
     */
    @PutMapping("/{id}")
    public Result update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Runner runner = runnerMapper.selectById(id);
        if (runner == null) {
            throw new BusinessException(404, "跑腿员不存在");
        }

        if (body.containsKey("name")) {
            runner.setName((String) body.get("name"));
        }
        if (body.containsKey("phone")) {
            runner.setPhone((String) body.get("phone"));
        }
        if (body.containsKey("idCard")) {
            runner.setIdCard((String) body.get("idCard"));
        }
        if (body.containsKey("status")) {
            runner.setStatus(((Number) body.get("status")).intValue());
        }
        if (body.containsKey("maxConcurrent")) {
            runner.setMaxConcurrent(((Number) body.get("maxConcurrent")).intValue());
        }

        runnerMapper.updateById(runner);
        return Result.success(runner);
    }
}
