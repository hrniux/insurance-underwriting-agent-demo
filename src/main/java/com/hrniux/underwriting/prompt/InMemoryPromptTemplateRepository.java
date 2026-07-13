package com.hrniux.underwriting.prompt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryPromptTemplateRepository implements PromptTemplateRepository {

    private final ConcurrentHashMap<String, List<PromptTemplateVersion>> templates = new ConcurrentHashMap<>();

    @Override
    public synchronized PromptTemplateVersion save(PromptTemplateVersion template) {
        List<PromptTemplateVersion> versions = new ArrayList<>(findByCode(template.code()));
        versions.add(template);
        versions.sort(Comparator.comparingInt(PromptTemplateVersion::version));
        templates.put(template.code(), List.copyOf(versions));
        return template;
    }

    @Override
    public List<PromptTemplateVersion> findByCode(String code) {
        return templates.getOrDefault(code, List.of());
    }

    @Override
    public Optional<PromptTemplateVersion> findByCodeAndVersion(String code, int version) {
        return findByCode(code).stream()
                .filter(template -> template.version() == version)
                .findFirst();
    }

    @Override
    public synchronized void replace(String code, List<PromptTemplateVersion> versions) {
        templates.put(code, List.copyOf(versions));
    }
}
