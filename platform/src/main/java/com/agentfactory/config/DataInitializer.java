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
        if (agentTypeRepository.count() > 0) return;

        log.info("Seeding default agent types...");

        var webScraper = new AgentType();
        webScraper.setName("web-scraper");
        webScraper.setDescription("HTTP requests, HTML parsing, text extraction");
        webScraper.setDefaultModel("gpt-4o");
        webScraper.setSandboxRequired(true);
        agentTypeRepository.save(webScraper);

        var codeAnalyst = new AgentType();
        codeAnalyst.setName("code-analyst");
        codeAnalyst.setDescription("File reading, code parsing, AST analysis");
        codeAnalyst.setDefaultModel("claude-sonnet-4");
        codeAnalyst.setSandboxRequired(true);
        agentTypeRepository.save(codeAnalyst);

        var generalPurpose = new AgentType();
        generalPurpose.setName("general-purpose");
        generalPurpose.setDescription("Pure LLM text generation, translation, summarization");
        generalPurpose.setDefaultModel("gpt-4o");
        generalPurpose.setSandboxRequired(false);
        agentTypeRepository.save(generalPurpose);

        log.info("Seeded 3 agent types: web-scraper, code-analyst, general-purpose");
    }
}
