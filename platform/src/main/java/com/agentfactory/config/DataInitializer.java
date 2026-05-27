package com.agentfactory.config;

import com.agentfactory.model.AgentType;
import com.agentfactory.repository.AgentTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private final AgentTypeRepository agentTypeRepository;

    public DataInitializer(AgentTypeRepository agentTypeRepository) {
        this.agentTypeRepository = agentTypeRepository;
    }

    @Override
    public void run(String... args) {
        seedIfMissing("web-scraper", "HTTP requests, HTML parsing, text extraction", "gpt-4o", true);
        seedIfMissing("code-analyst", "File reading, code parsing, AST analysis", "claude-sonnet-4", true);
        seedIfMissing("general-purpose", "Pure LLM text generation, translation, summarization", "gpt-4o", false);
        seedIfMissing("coding-agent", "Clone repo, locate bugs, fix code, run tests, produce patches and MRs", "claude-sonnet-4", false);
    }

    private void seedIfMissing(String name, String description, String defaultModel, boolean sandboxRequired) {
        if (agentTypeRepository.findAll().stream().anyMatch(at -> at.getName().equals(name))) {
            return;
        }
        log.info("Seeding agent type: {}", name);
        var agentType = new AgentType();
        agentType.setName(name);
        agentType.setDescription(description);
        agentType.setDefaultModel(defaultModel);
        agentType.setSandboxRequired(sandboxRequired);
        agentTypeRepository.save(agentType);
    }
}
