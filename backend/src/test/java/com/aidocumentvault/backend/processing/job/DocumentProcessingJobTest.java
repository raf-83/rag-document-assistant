package com.aidocumentvault.backend.processing.job;

import com.aidocumentvault.backend.document.entity.Document;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DocumentProcessingJobTest {

    private final Document document = mock(Document.class);
    private final UUID correlationId = UUID.randomUUID();

    @Test
    void shouldCreatePendingJob() {
        var job = new DocumentProcessingJob(document, correlationId);

        assertThat(job.getDocument()).isSameAs(document);
        assertThat(job.getCorrelationId()).isEqualTo(correlationId);
        assertThat(job.getStatus()).isEqualTo(DocumentProcessingJobStatus.PENDING);
    }

    @Test
    void shouldRejectNullDocument() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DocumentProcessingJob(null, correlationId))
            .withMessage("document must not be null");
    }

    @Test
    void shouldRejectNullCorrelationId() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DocumentProcessingJob(document, null))
            .withMessage("correlationId must not be null");
    }

    @Test
    void shouldStartPendingJob() {
        var job = newJob();

        job.start();

        assertThat(job.getStatus()).isEqualTo(DocumentProcessingJobStatus.RUNNING);
    }

    @Test
    void shouldSucceedRunningJob() {
        var job = runningJob();

        job.succeed();

        assertThat(job.getStatus()).isEqualTo(DocumentProcessingJobStatus.SUCCEEDED);
    }

    @Test
    void shouldFailRunningJob() {
        var job = runningJob();

        job.fail();

        assertThat(job.getStatus()).isEqualTo(DocumentProcessingJobStatus.FAILED);
    }

    @Test
    void shouldCompleteSuccessfulLifecycle() {
        var job = newJob();

        job.start();
        job.succeed();

        assertThat(job.getStatus()).isEqualTo(DocumentProcessingJobStatus.SUCCEEDED);
    }

    @Test
    void shouldCompleteFailedLifecycle() {
        var job = newJob();

        job.start();
        job.fail();

        assertThat(job.getStatus()).isEqualTo(DocumentProcessingJobStatus.FAILED);
    }

    @Test
    void shouldRejectSucceedingPendingJobAndPreserveStatus() {
        var job = newJob();

        assertRejectedTransition(
            job,
            job::succeed,
            DocumentProcessingJobStatus.PENDING,
            DocumentProcessingJobStatus.SUCCEEDED
        );
    }

    @Test
    void shouldRejectFailingPendingJobAndPreserveStatus() {
        var job = newJob();

        assertRejectedTransition(
            job,
            job::fail,
            DocumentProcessingJobStatus.PENDING,
            DocumentProcessingJobStatus.FAILED
        );
    }

    @Test
    void shouldRejectStartingRunningJobAndPreserveStatus() {
        var job = runningJob();

        assertRejectedTransition(
            job,
            job::start,
            DocumentProcessingJobStatus.RUNNING,
            DocumentProcessingJobStatus.RUNNING
        );
    }

    @Test
    void shouldRejectSucceedingSucceededJobAndPreserveStatus() {
        var job = succeededJob();

        assertRejectedTransition(
            job,
            job::succeed,
            DocumentProcessingJobStatus.SUCCEEDED,
            DocumentProcessingJobStatus.SUCCEEDED
        );
    }

    @Test
    void shouldRejectFailingFailedJobAndPreserveStatus() {
        var job = failedJob();

        assertRejectedTransition(
            job,
            job::fail,
            DocumentProcessingJobStatus.FAILED,
            DocumentProcessingJobStatus.FAILED
        );
    }

    @Test
    void shouldRejectFailingSucceededJobAndPreserveStatus() {
        var job = succeededJob();

        assertRejectedTransition(
            job,
            job::fail,
            DocumentProcessingJobStatus.SUCCEEDED,
            DocumentProcessingJobStatus.FAILED
        );
    }

    @Test
    void shouldRejectSucceedingFailedJobAndPreserveStatus() {
        var job = failedJob();

        assertRejectedTransition(
            job,
            job::succeed,
            DocumentProcessingJobStatus.FAILED,
            DocumentProcessingJobStatus.SUCCEEDED
        );
    }

    @Test
    void shouldRejectStartingTerminalJobsAndPreserveStatuses() {
        var succeededJob = succeededJob();
        var failedJob = failedJob();

        assertRejectedTransition(
            succeededJob,
            succeededJob::start,
            DocumentProcessingJobStatus.SUCCEEDED,
            DocumentProcessingJobStatus.RUNNING
        );
        assertRejectedTransition(
            failedJob,
            failedJob::start,
            DocumentProcessingJobStatus.FAILED,
            DocumentProcessingJobStatus.RUNNING
        );
    }

    private DocumentProcessingJob newJob() {
        return new DocumentProcessingJob(document, correlationId);
    }

    private DocumentProcessingJob runningJob() {
        var job = newJob();
        job.start();
        return job;
    }

    private DocumentProcessingJob succeededJob() {
        var job = runningJob();
        job.succeed();
        return job;
    }

    private DocumentProcessingJob failedJob() {
        var job = runningJob();
        job.fail();
        return job;
    }

    private void assertRejectedTransition(
        DocumentProcessingJob job,
        Runnable transition,
        DocumentProcessingJobStatus currentStatus,
        DocumentProcessingJobStatus requestedStatus
    ) {
        assertThatThrownBy(transition::run)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "Cannot change processing job status from "
                    + currentStatus
                    + " to "
                    + requestedStatus
            );

        assertThat(job.getStatus()).isEqualTo(currentStatus);
    }
}
