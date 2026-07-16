package com.campus.helper.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.helper.common.BusinessException;
import com.campus.helper.common.Result;
import com.campus.helper.entity.Runner;
import com.campus.helper.entity.Student;
import com.campus.helper.mapper.RunnerMapper;
import com.campus.helper.mapper.StudentMapper;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class LoginController {

    private final StudentMapper studentMapper;
    private final RunnerMapper runnerMapper;

    private static final String DEFAULT_PASSWORD = "123456";

    public LoginController(StudentMapper studentMapper, RunnerMapper runnerMapper) {
        this.studentMapper = studentMapper;
        this.runnerMapper = runnerMapper;
    }

    /**
     * Student login — studentNo + password.
     */
    @PostMapping("/student/login")
    public Result studentLogin(@RequestBody Map<String, String> body) {
        String studentNo = body.get("studentNo");
        String password = body.get("password");

        if (studentNo == null || studentNo.isBlank()) {
            throw new BusinessException(400, "请输入学号");
        }
        if (password == null || password.isBlank()) {
            throw new BusinessException(400, "请输入密码");
        }

        Student student = studentMapper.selectOne(
                new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, studentNo));
        if (student == null) {
            throw new BusinessException(404, "学号不存在");
        }
        if (student.getStatus() != null && student.getStatus() == 0) {
            throw new BusinessException(403, "该账号已被禁用");
        }
        if (!password.equals(student.getPassword())) {
            throw new BusinessException(401, "密码错误");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", student.getId());
        result.put("studentNo", student.getStudentNo());
        result.put("name", student.getName());
        result.put("phone", student.getPhone());
        result.put("role", "STUDENT");
        return Result.success(result);
    }

    /**
     * Student registration.
     */
    @PostMapping("/student/register")
    public Result studentRegister(@RequestBody Map<String, String> body) {
        String studentNo = body.get("studentNo");
        String name = body.get("name");
        String phone = body.get("phone");
        String dormitory = body.get("dormitory");
        String password = body.get("password");

        if (studentNo == null || studentNo.isBlank()) throw new BusinessException(400, "请输入学号");
        if (name == null || name.isBlank()) throw new BusinessException(400, "请输入姓名");
        if (phone == null || phone.isBlank()) throw new BusinessException(400, "请输入手机号");
        if (dormitory == null || dormitory.isBlank()) throw new BusinessException(400, "请输入宿舍楼");

        // Check duplicate studentNo
        Long count = studentMapper.selectCount(
                new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, studentNo));
        if (count > 0) {
            throw new BusinessException(400, "该学号已注册");
        }

        Student student = new Student();
        student.setStudentNo(studentNo);
        student.setName(name);
        student.setPhone(phone);
        student.setDormitory(dormitory);
        student.setPassword(password != null && !password.isBlank() ? password : DEFAULT_PASSWORD);
        student.setStatus(1);
        student.setTotalOrders(0);
        studentMapper.insert(student);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", student.getId());
        result.put("studentNo", student.getStudentNo());
        result.put("name", student.getName());
        result.put("role", "STUDENT");
        return Result.success(result);
    }

    /**
     * Runner login — phone + password.
     */
    @PostMapping("/runner/login")
    public Result runnerLogin(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String password = body.get("password");

        if (phone == null || phone.isBlank()) throw new BusinessException(400, "请输入手机号");
        if (password == null || password.isBlank()) throw new BusinessException(400, "请输入密码");

        Runner runner = runnerMapper.selectOne(
                new LambdaQueryWrapper<Runner>().eq(Runner::getPhone, phone));
        if (runner == null) {
            throw new BusinessException(404, "手机号未注册，请先注册");
        }
        if (runner.getStatus() == 2) {
            throw new BusinessException(403, "该账号已被停用，请联系管理员");
        }
        if (!password.equals(runner.getPassword())) {
            throw new BusinessException(401, "密码错误");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", runner.getId());
        result.put("runnerNo", runner.getRunnerNo());
        result.put("name", runner.getName());
        result.put("phone", runner.getPhone());
        result.put("rating", runner.getRating());
        result.put("role", "RUNNER");
        return Result.success(result);
    }

    /**
     * Admin login — username + password.
     * Default: admin / admin123
     */
    @PostMapping("/admin/login")
    public Result adminLogin(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (!"admin".equals(username) || !"admin123".equals(password)) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", 0L);
        result.put("name", "管理员");
        result.put("role", "ADMIN");
        return Result.success(result);
    }
}
