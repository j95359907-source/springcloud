package com.example.school.clazz.repository;

import com.example.school.clazz.model.SchoolClass;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 班级数据访问层
 */
public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {
}
