package com.campus.helper.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.helper.common.BusinessException;
import com.campus.helper.common.Result;
import com.campus.helper.entity.Runner;
import com.campus.helper.entity.Student;
import com.campus.helper.mapper.RunnerMapper;
import com.campus.helper.mapper.StudentMapper;
import com.campus.helper.security.AuthenticatedUser;
import com.campus.helper.security.JwtService;
import com.campus.helper.security.LoginAttemptService;
import com.campus.helper.security.SecurityUtils;
import com.campus.helper.security.TokenVersionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class LoginController {

    private final StudentMapper studentMapper;
    private final RunnerMapper runnerMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final TokenVersionService tokenVersionService;
    private final String adminUsername;
    private final String adminPassword;
    private final String adminPasswordHash;

    public LoginController(StudentMapper studentMapper, RunnerMapper runnerMapper,
                           PasswordEncoder passwordEncoder, JwtService jwtService,
                           LoginAttemptService loginAttemptService,
                           TokenVersionService tokenVersionService,
                           @Value("${security.admin.username}") String adminUsername,
                           @Value("${security.admin.password}") String adminPassword,
                           @Value("${security.admin.password-hash:}") String adminPasswordHash) {
        this.studentMapper = studentMapper;
        this.runnerMapper = runnerMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.tokenVersionService = tokenVersionService;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.adminPasswordHash = adminPasswordHash;

        if (adminPasswordHash == null || adminPasswordHash.isBlank()) {
            log.warn("[安全告警] 管理员密码以明文配置在 security.admin.password 中。"
                    + "请改用 security.admin.password-hash（BCrypt 哈希），"
                    + "生成方式见 .env.example 中的 ADMIN_PASSWORD_HASH 说明。");
        }
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

        loginAttemptService.assertNotLocked("STUDENT", studentNo);
        if (!passwordMatches(password, student.getPassword())) {
            loginAttemptService.recordFailure("STUDENT", studentNo);
            throw new BusinessException(401, "密码错误");
        }
        loginAttemptService.reset("STUDENT", studentNo);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", student.getId());
        result.put("studentNo", student.getStudentNo());
        result.put("name", student.getName());
        result.put("phone", student.getPhone());
        result.put("role", "STUDENT");
        addToken(result, student.getId(), "STUDENT", student.getName());
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
        if (password == null || password.length() < 6) throw new BusinessException(400, "密码至少6位");

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
        student.setPassword(passwordEncoder.encode(password));
        student.setStatus(1);
        student.setTotalOrders(0);
        studentMapper.insert(student);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", student.getId());
        result.put("studentNo", student.getStudentNo());
        result.put("name", student.getName());
        result.put("role", "STUDENT");
        addToken(result, student.getId(), "STUDENT", student.getName());
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

        loginAttemptService.assertNotLocked("RUNNER", phone);
        if (!passwordMatches(password, runner.getPassword())) {
            loginAttemptService.recordFailure("RUNNER", phone);
            throw new BusinessException(401, "密码错误");
        }
        loginAttemptService.reset("RUNNER", phone);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", runner.getId());
        result.put("runnerNo", runner.getRunnerNo());
        result.put("name", runner.getName());
        result.put("phone", runner.getPhone());
        result.put("rating", runner.getRating());
        result.put("role", "RUNNER");
        addToken(result, runner.getId(), "RUNNER", runner.getName());
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

        loginAttemptService.assertNotLocked("ADMIN", String.valueOf(username));
        if (!adminCredentialsMatch(username, password)) {
            loginAttemptService.recordFailure("ADMIN", String.valueOf(username));
            throw new BusinessException(401, "用户名或密码错误");
        }
        loginAttemptService.reset("ADMIN", String.valueOf(username));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", 0L);
        result.put("name", "管理员");
        result.put("role", "ADMIN");
        addToken(result, 0L, "ADMIN", "管理员");
        return Result.success(result);
    }

    /**
     * Verify the admin credentials.
     *
     * <p>Prefers the BCrypt hash when one is configured. Falling back to the plaintext
     * password keeps existing deployments working while the startup warning asks for a
     * migration; both branches compare in constant time so the check itself leaks nothing.
     */
    private boolean adminCredentialsMatch(String username, String password) {
        if (username == null || password == null) {
            return false;
        }
        if (!constantTimeEquals(adminUsername, username)) {
            return false;
        }
        if (adminPasswordHash != null && !adminPasswordHash.isBlank()) {
            return passwordEncoder.matches(password, adminPasswordHash);
        }
        return constantTimeEquals(adminPassword, password);
    }

    /**
     * Stored passwords are always BCrypt — {@code PasswordMigrationRunner} converts any
     * legacy plaintext row at startup. Comparing plaintext here would mean read access to
     * the database is enough to log in as any user, so a non-BCrypt value fails closed and
     * is repaired by the migration on the next restart.
     */
    private boolean passwordMatches(String rawPassword, String storedPassword) {
        return isBcrypt(storedPassword) && passwordEncoder.matches(rawPassword, storedPassword);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isBcrypt(String password) {
        return password != null && (password.startsWith("$2a$")
                || password.startsWith("$2b$") || password.startsWith("$2y$"));
    }

    private void addToken(Map<String, Object> result, Long userId, String role, String name) {
        // Bind the token to the account's current version so it can be revoked later.
        Long version = tokenVersionService.currentVersion(role, userId);
        JwtService.TokenResult token =
                jwtService.issue(userId, role, name, version == null ? 0L : version);
        result.put("token", token.token());
        result.put("expiresAt", token.expiresAt());
    }

    /**
     * Sign out everywhere: bumping the version invalidates every token already issued to this
     * account, including the one making this request. A JWT cannot be recalled by itself, so
     * the server keeps a per-account version and the token carries the value it was issued at.
     */
    @PostMapping("/logout")
    public Result logout(Authentication authentication) {
        AuthenticatedUser user = SecurityUtils.current(authentication);
        tokenVersionService.bump(user.role(), user.userId());
        return Result.success(Map.of("message", "已退出登录，该账号在所有设备上的会话均已失效"));
    }
}
