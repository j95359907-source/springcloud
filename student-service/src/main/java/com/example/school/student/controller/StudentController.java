package com.example.school.student.controller;

import com.example.school.student.model.Student;
import com.example.school.student.repository.StudentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 学生 CRUD 接口（内存版）
 */
@RestController
@RequestMapping("/student")
public class StudentController {
    private final StudentRepository repository;

    public StudentController(StudentRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Student> list() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public Student getStudent(@PathVariable("id") Long id) {
        return repository.findById(id).orElse(null);
    }

    @PostMapping
    public Student create(@RequestBody Student student) {
        student.setId(null);
        return repository.save(student);
    }

    @PutMapping("/{id}")
    public Student update(@PathVariable("id") Long id, @RequestBody Student student) {
        student.setId(id);
        return repository.save(student);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable("id") Long id) {
        boolean exists = repository.existsById(id);
        if (exists) {
            repository.deleteById(id);
        }
        return Map.of("success", exists, "id", id);
    }

    /*
     * ==================== 面试题（学生 CRUD） ====================
     * Q1：为什么 create 里要 student.setId(null)？
     * A：防止前端传入 id 覆盖主键，确保由数据库自增生成新记录。
     *
     * Q2：update 为什么直接 save？
     * A：JPA save 对有主键对象执行更新；无主键对象执行插入。
     *
     * Q3：delete 为什么先 existsById？
     * A：先判断存在性可返回更友好的业务结果（success=true/false）。
     * ============================================================
     */
}
