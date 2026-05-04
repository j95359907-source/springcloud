package com.example.school.student.repository;

import com.example.school.student.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 学生数据访问层
 */
public interface StudentRepository extends JpaRepository<Student, Long> {
}
