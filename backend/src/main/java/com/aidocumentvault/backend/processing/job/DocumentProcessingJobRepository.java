package com.aidocumentvault.backend.processing.job;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentProcessingJobRepository extends JpaRepository<DocumentProcessingJob, UUID> {
}
