package com.campus.helper.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.helper.common.BusinessException;
import com.campus.helper.common.Result;
import com.campus.helper.entity.Student;
import com.campus.helper.mapper.StudentMapper;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentMapper studentMapper;

    public StudentController(StudentMapper studentMapper) {
        this.studentMapper = studentMapper;
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
        Page<Student> result = studentMapper.selectPage(new Page<>(page, size), wrapper);
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
            student.setStatus(((Number) body.get("status")).intValue());
        }
        if (body.containsKey("dormitory")) {
            student.setDormitory((String) body.get("dormitory"));
        }
        studentMapper.updateById(student);
        return Result.success(student);
    }
}
