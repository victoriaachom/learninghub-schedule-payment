package edu.lms.service;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {
    Optional<ClassSession> findFirstByCourseId(String courseId);
    List<ClassSession> findByCourseId(String courseId);
    List<ClassSession> findAllByOrderByStartTimeAsc();
}
