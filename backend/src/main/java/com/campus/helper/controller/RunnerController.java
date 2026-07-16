package com.campus.helper.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.helper.common.Result;
import com.campus.helper.entity.Runner;
import com.campus.helper.mapper.RunnerMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/runners")
public class RunnerController {

    private final RunnerMapper runnerMapper;

    public RunnerController(RunnerMapper runnerMapper) {
        this.runnerMapper = runnerMapper;
    }

    @GetMapping
    public Result list(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Runner> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Runner::getStatus, status);
        }
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
        return Result.success(runner);
    }
}
