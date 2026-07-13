package com.hrniux.underwriting.prompt;

import java.util.List;
import java.util.Optional;

public interface PromptTemplateRepository {

    PromptTemplateVersion save(PromptTemplateVersion template);

    List<PromptTemplateVersion> findByCode(String code);

    List<PromptTemplateVersion> findAll();

    Optional<PromptTemplateVersion> findByCodeAndVersion(String code, int version);

    void replace(String code, List<PromptTemplateVersion> versions);
}
