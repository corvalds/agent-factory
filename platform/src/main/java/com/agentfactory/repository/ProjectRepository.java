package com.agentfactory.repository;

import com.agentfactory.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByActiveTrueOrderByNameAsc();

    Optional<Project> findByName(String name);

    @Query("SELECT p FROM Project p WHERE p.active = true AND (" +
           "LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(p.keywords) LIKE LOWER(CONCAT('%', :q, '%')))")
    List<Project> search(@Param("q") String query);
}
