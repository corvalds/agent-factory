package com.agentfactory.controller;

import com.agentfactory.model.Project;
import com.agentfactory.repository.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;

    public ProjectController(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @GetMapping
    public List<Project> list(@RequestParam(required = false, defaultValue = "true") boolean activeOnly) {
        if (activeOnly) {
            return projectRepository.findByActiveTrueOrderByNameAsc();
        }
        return projectRepository.findAll();
    }

    @GetMapping("/search")
    public List<Project> search(@RequestParam String q) {
        return projectRepository.search(q);
    }

    @GetMapping("/{id}")
    public Project get(@PathVariable Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Project create(@RequestBody Project project) {
        return projectRepository.save(project);
    }

    @PutMapping("/{id}")
    public Project update(@PathVariable Long id, @RequestBody Project project) {
        Project existing = projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (project.getName() != null) existing.setName(project.getName());
        if (project.getRepoUrl() != null) existing.setRepoUrl(project.getRepoUrl());
        if (project.getDefaultBranch() != null) existing.setDefaultBranch(project.getDefaultBranch());
        if (project.getDescription() != null) existing.setDescription(project.getDescription());
        if (project.getKeywords() != null) existing.setKeywords(project.getKeywords());
        existing.setActive(project.isActive());
        return projectRepository.save(existing);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        projectRepository.deleteById(id);
    }
}
