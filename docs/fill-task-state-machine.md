# Fill Task State Machine

## States

- `PENDING`: task created, waiting for MQ consumer.
- `RUNNING`: consumer is processing this task.
- `SUCCESS`: processing completed and downloadable result exists.
- `FAILED`: processing failed due to non-timeout error.
- `TIMEOUT`: processing timed out (AI timeout / running timeout).

## Transition Rules

- `PENDING -> RUNNING`
  - Trigger: consumer starts processing.
- `RUNNING -> SUCCESS`
  - Trigger: all task steps finished successfully.
- `RUNNING -> FAILED`
  - Trigger: exception during processing and not classified as timeout.
- `RUNNING -> TIMEOUT`
  - Trigger: timeout exception from AI flow.
- `RUNNING -> TIMEOUT -> RUNNING` (auto recovery)
  - Trigger: duplicate/redelivered message detects old `RUNNING` exceeded `fill.task.running-timeout-minutes`, then recovers and reruns.
- `FAILED/TIMEOUT -> PENDING` (manual rerun)
  - Trigger: `POST /api/fill/tasks/{taskId}/rerun`.

## Duplicate Delivery Protection

- If task is already `SUCCESS`, consumer idempotently `ACK`s and skips.
- If task is `RUNNING` and not timed out, consumer `ACK`s and skips duplicate delivery.

## Rerun Entry

- API: `POST /api/fill/tasks/{taskId}/rerun`
- Allowed source states: `FAILED`, `TIMEOUT`
- Action:
  - reset task to `PENDING`
  - clear previous result path and finished time
  - enqueue task to MQ again

## Frontend Action Hints

- `SUCCESS`: allow `DOWNLOAD`.
- `FAILED/TIMEOUT`: allow `MANUAL_RERUN`.
