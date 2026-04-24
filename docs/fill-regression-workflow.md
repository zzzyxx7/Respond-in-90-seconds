# Fill Regression Workflow (Apifox + Script)

## Fixed Regression Scope

- login
- upload document
- upload template
- submit fill task
- poll task status until terminal state
- download result file

Terminal states: `SUCCESS`, `FAILED`, `TIMEOUT`.

## Apifox Case Order

1. `POST /api/auth/login`
2. `POST /api/documents/upload` (multipart file)
3. `POST /api/templates/upload` (multipart file)
4. `POST /api/fill/submit`
5. `GET /api/fill/tasks/{taskId}` (loop/poll)
6. `GET /api/fill/download/{taskId}`

## Suggested Assertions

- submit response contains `data.id` (taskId)
- poll response contains:
  - `status`
  - `allowedActions`
  - `failureStage` / `failureReasonCode` when failed
- if `status=SUCCESS`, download returns attachment stream
- if `status=FAILED/TIMEOUT`, call `POST /api/fill/tasks/{taskId}/rerun` and verify task becomes `PENDING/RUNNING`

## Script Regression

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\fill-regression.ps1 `
  -BaseUrl "http://127.0.0.1:8080" `
  -Username "admin" `
  -Password "123456" `
  -DocPath "D:\code\doc-fusion\test\docs\sample.pdf" `
  -TemplatePath "D:\code\doc-fusion\test\templates\sample.xlsx" `
  -OutputFile "D:\code\doc-fusion\test\out\result.xlsx"
```

## AI Replacement Friendly

- Backend contract is fixed to task status + download behavior.
- When AI implementation changes, rerun this workflow without changing API paths.
