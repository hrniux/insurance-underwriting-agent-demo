package com.hrniux.underwriting.task;

import java.util.List;
import java.util.Optional;

public interface UnderwritingTaskRepository {

    UnderwritingTask save(UnderwritingTask task);

    Optional<UnderwritingTask> findById(String id);

    List<UnderwritingTask> findAll();

    void deleteById(String id);
}
