package com.campus.helper.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.helper.common.BusinessException;
import com.campus.helper.common.PageQuery;
import com.campus.helper.common.Result;
import com.campus.helper.entity.Student;
import com.campus.helper.mapper.StudentMapper;
import com.campus.helper.security.TokenVersionService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentMapper studentMapper;
    private final TokenVersionService tokenVersionService;

    public StudentController(StudentMapper studentMapper, TokenVersionService tokenVersionService) {
        this.studentMapper = studentMapper;
        this.tokenVersionService = tokenVersionService;
    }

    /** Paginated student list with optional status filter */
    @GetMapping
    public Result list(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Student::getStatus, status);
        }
        wrapper.orderByDesc(Student::getCreatedAt);
        Page<Student> result = studentMapper.selectPage(PageQuery.of(page, size), wrapper);
        return Result.success(result);
    }

    /** Update student info — admin only */
    @PutMapping("/{id}")
    public Result update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Student student = studentMapper.selectById(id);
        if (student == null) {
            throw new BusinessException(404, "学生不存在");
        }
        if (body.containsKey("status")) {
            int newStatus = ((Number) body.get("status")).intValue();
            student.setStatus(newStatus);
            if (newStatus == 0) {
                // Disabling an account has to end its sessions too. Without this the student
                // keeps working with a token that stays valid for up to 12 more hours.
                tokenVersionService.bump("STUDENT", id);
            }
        }
        if (body.containsKey("dormitory")) {
            student.setDormitory((String) body.get("dormitory"));
        }
        studentMapper.updateById(student);
        return Result.success(student);
    }

    /**
     * Kill every live session for a student without disabling the account — they can log in
     * again immediately. Guarded by the ADMIN-only rule on {@code /api/students/**}.
     */
    @PostMapping("/{id}/revoke-tokens")
    public Result revokeTokens(@PathVariable Long id) {
        if (studentMapper.selectById(id) == null) {
            throw new BusinessException(404, "学生不存在");
        }
        tokenVersionService.bump("STUDENT", id);
        return Result.success(Map.of("message", "该学生的所有会话已失效，需重新登录"));
    }
}
