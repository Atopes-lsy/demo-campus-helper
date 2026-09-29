package com.campus.helper.security;

import com.campus.helper.entity.Runner;
import com.campus.helper.entity.Student;
import com.campus.helper.mapper.RunnerMapper;
import com.campus.helper.mapper.StudentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordMigrationRunner implements ApplicationRunner {

    private final StudentMapper studentMapper;
    private final RunnerMapper runnerMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int migrated = 0;
        for (Student student : studentMapper.selectList(null)) {
            if (!isBcrypt(student.getPassword())) {
                student.setPassword(passwordEncoder.encode(student.getPassword()));
                studentMapper.updateById(student);
                migrated++;
            }
        }
        for (Runner runner : runnerMapper.selectList(null)) {
            if (!isBcrypt(runner.getPassword())) {
                runner.setPassword(passwordEncoder.encode(runner.getPassword()));
                runnerMapper.updateById(runner);
                migrated++;
            }
        }
        if (migrated > 0) {
            log.info("Migrated {} plaintext passwords to BCrypt", migrated);
        }
    }

    private boolean isBcrypt(String password) {
        return password != null && password.matches("^\\$2[aby]\\$.*");
    }
}
