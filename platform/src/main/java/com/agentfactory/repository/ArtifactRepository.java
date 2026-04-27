package com.agentfactory.repository;

import com.agentfactory.model.Artifact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ArtifactRepository extends JpaRepository<Artifact, UUID> {

    List<Artifact> findByTaskIdOrderBySortOrderAsc(Long taskId);

    @Query("SELECT COALESCE(MAX(a.sortOrder), 0) FROM Artifact a WHERE a.task.id = :taskId")
    int findMaxSortOrderByTaskId(Long taskId);
}
