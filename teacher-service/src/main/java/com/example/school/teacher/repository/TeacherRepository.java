package com.example.school.teacher.repository;

import com.example.school.teacher.model.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 老师数据访问层
 */
public interface TeacherRepository extends JpaRepository<Teacher, Long> {
}
