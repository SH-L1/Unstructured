-- Basic reference data
select id, code, name, active, created_at, updated_at
from departments
order by code;

select id, document_type, title, source_name, legal_basis, created_at, updated_at
from knowledge_documents
order by id;

select c.id, c.knowledge_document_id, d.title, c.chunk_index, c.active, c.legal_basis
from knowledge_document_chunks c
join knowledge_documents d on d.id = c.knowledge_document_id
order by d.id, c.chunk_index;

-- Complaint intake and analysis
select id, receipt_number, title, source_channel, status, location_text, created_at, updated_at
from complaints
order by created_at desc;

select a.id, a.complaint_id, a.complaint_type, a.intent, a.urgency, a.sentiment,
       d.code as department_code, d.name as department_name, a.created_at, a.updated_at
from complaint_analysis a
join departments d on d.id = a.department_id
order by a.created_at desc;

-- Drafts and RAG evidence
select id, complaint_id, status, model_name, created_at, updated_at
from official_drafts
order by created_at desc;

select r.id, r.complaint_id, r.official_draft_id, r.knowledge_document_id,
       r.knowledge_document_chunk_id, r.score, r.legal_basis, r.created_at
from rag_contexts r
order by r.created_at desc, r.score desc;

select id, official_draft_id, revised_by, created_at
from draft_revisions
order by created_at desc;

-- Attachments, audit and API users
select id, complaint_id, original_filename, content_type, size, storage_key, created_at
from complaint_attachments
order by created_at desc;

select id, http_method, request_path, actor, status_code, duration_ms, created_at
from audit_logs
order by created_at desc;

select id, username, role, active, created_at, updated_at
from api_users
order by username;

-- Flyway state
select installed_rank, version, description, success, installed_on
from flyway_schema_history
order by installed_rank;
