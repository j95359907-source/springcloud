package com.example.school.teacher.controller;

import com.example.school.teacher.model.Teacher;
import com.example.school.teacher.repository.TeacherRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 老师 CRUD 接口（内存版）
 */
@RestController
@RequestMapping("/teacher")
public class TeacherController {
    private final TeacherRepository repository;

    public TeacherController(TeacherRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Teacher> list() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public Teacher getTeacher(@PathVariable("id") Long id) {
        return repository.findById(id).orElse(null);
    }

    @PostMapping
    public Teacher create(@RequestBody Teacher teacher) {
        teacher.setId(null);
        return repository.save(teacher);
    }

    @PutMapping("/{id}")
    public Teacher update(@PathVariable("id") Long id, @RequestBody Teacher teacher) {
        teacher.setId(id);
        return repository.save(teacher);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable("id") Long id) {
        boolean exists = repository.existsById(id);
        if (exists) {
            repository.deleteById(id);
        }
        return Map.of("success", exists, "id", id);
    }
}
