# DB Schema Review

## Current Scope

The database is managed by Flyway and validated by JPA. Hibernate must not create or update tables automatically.

Core tables:

- `complaints`: civil complaint intake, receipt number, title, source channel, raw text, location and status.
- `complaint_attachments`: uploaded evidence metadata and storage key.
- `complaint_analysis`: LLM/mock analysis output, complaint type, urgency, sentiment, department and GeoJSON.
- `knowledge_documents`: source law, ordinance, manual, FAQ or case metadata.
- `knowledge_document_chunks`: searchable RAG chunk units derived from knowledge documents.
- `rag_contexts`: actual RAG references used for a complaint or official draft.
- `official_drafts`: generated response draft.
- `draft_revisions`: officer edits to a generated draft.
- `departments`: routing target departments.
- `audit_logs`: API access audit trail.
- `api_users`: API key users and roles.

## Confirmed Enums

- `ComplaintStatus`: `RECEIVED`, `ANALYZED`, `DRAFT_GENERATED`, `IN_PROGRESS`, `COMPLETED`, `REJECTED`
- `ComplaintType`: `ILLEGAL_DUMPING`, `ROAD_DAMAGE`, `ILLEGAL_PARKING`, `TRAFFIC_SIGN`, `NOISE`, `ENVIRONMENT`, `GENERAL`
- `SourceChannel`: `WEB`, `MOBILE`, `CALL_CENTER`, `VISIT`, `EMAIL`, `OPEN_API`
- `AttachmentType`: `IMAGE`, `PDF`, `DOCUMENT`, `SPREADSHEET`, `ARCHIVE`, `OTHER`
- `Urgency`: `LOW`, `NORMAL`, `HIGH`, `EMERGENCY`
- `Sentiment`: `NEUTRAL`, `DISCOMFORT`, `ANGER`, `ANXIETY`
- `DraftStatus`: `DRAFT`, `REVISED`, `APPROVED`, `REJECTED`
- `DocumentType`: `LAW`, `ORDINANCE`, `MANUAL`, `FAQ`, `CASE`, `SYNTHETIC`
- `ApiUserRole`: `ADMIN`, `OFFICER`, `VIEWER`

## Migration Policy

Schema changes must be added through new Flyway files:

- `V1__create_complaint_schema.sql`
- `V2__create_audit_logs.sql`
- `V3__create_api_users.sql`
- `V4__refine_complaints_and_add_knowledge_chunks.sql`

After each migration change, run:

```powershell
cd C:\Users\user\Documents\GitHub\Unstructured\egov-boot-web
mvn test
```

## DBeaver Check

Use `docs/db-check-queries.sql` to inspect the current data shape, seed data, RAG chunks, generated drafts, audit logs and Flyway history.
