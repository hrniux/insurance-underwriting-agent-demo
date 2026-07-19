package com.hrniux.underwriting.task;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryUnderwritingTaskRepository implements UnderwritingTaskRepository {

    private final ConcurrentHashMap<String, UnderwritingTask> tasks = new ConcurrentHashMap<>();

    @Override
    public UnderwritingTask save(UnderwritingTask task) {
        tasks.put(task.id(), task);
        return task;
    }

    @Override
    public Optional<UnderwritingTask> findById(String id) {
        return Optional.ofNullable(tasks.get(id));
    }

    @Override
    public List<UnderwritingTask> findAll() {
        return tasks.values().stream()
                .sorted(Comparator.comparing(UnderwritingTask::createdAt).reversed())
                .toList();
    }

    @Override
    public void deleteById(String id) {
        tasks.remove(id);
    }
}
