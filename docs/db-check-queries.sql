-- Complaint workflow
select id, receipt_number, status, workflow_blocker, version, created_at, updated_at
from complaints
order by created_at desc;

select id, complaint_id, issue_index, complaint_type, jurisdiction_status,
       safety_risk, processability, status, version
from complaint_issues
order by complaint_id, issue_index;

-- Jobs and failures
select id, complaint_id, job_type, status, attempts, max_attempts,
       lease_until, failure_reason, result_reference, payload_reference, version, created_at
from processing_jobs
order by created_at desc;

-- Governed knowledge and immutable evidence
select id, name, source_type, base_url, jurisdiction_code, status,
       collection_interval_minutes, last_verified_at, last_successful_sync_at,
       last_failure_at, last_failure_reason, next_sync_at, stale_after, last_content_hash
from source_registry
order by name;

select id, source_registry_id, external_id, title, jurisdiction_code,
       effective_from, effective_to, status, content_hash
from legal_document_versions
order by source_registry_id, external_id, effective_from desc;

select id, legal_document_version_id, provision_key, heading, content
from legal_provisions
order by legal_document_version_id, provision_key;

select id, document_type, title, purpose, verification_status, jurisdiction_code,
       effective_from, effective_to, content_hash, source_version, source_registry_id
from knowledge_documents
order by id;

select id, complaint_id, retrieval_run_id, source_type, title, legal_basis, source_version, source_status,
       jurisdiction_code, effective_from, effective_to, content_hash, supports_claim, created_at
from evidence_snapshots
order by created_at desc;

select c.id, c.official_draft_id, c.claim_index, c.claim_type, c.claim_text, c.source_document_ids,
       l.evidence_snapshot_id, l.relation_type
from draft_claims c
left join claim_evidence_links l on l.draft_claim_id = c.id
order by c.official_draft_id, c.claim_index;

-- Deterministic verification and human decisions
select id, complaint_id, official_draft_id, rule_code, status, hard_failure, message, created_at
from verification_results
order by created_at desc;

select id, complaint_id, official_draft_id, action, actor, actor_role, notes, created_at
from human_reviews
order by created_at desc;

select id, entity_type, entity_id, action, actor, actor_role,
       before_value, after_value, idempotency_key, created_at
from workflow_audit_events
order by created_at desc;

-- AI audit metadata. Ranking diagnostics are intentionally not an approval signal.
select id, complaint_id, processing_job_id, task_type, provider, model_name,
       prompt_version, schema_version, input_hash, output_hash, status,
       cost_units, duration_ms, retry_count, failure_reason, created_at
from ai_runs
order by created_at desc;

-- Attachment quarantine
select a.id, a.complaint_id, a.original_filename, a.content_type, a.size,
       x.quarantine_status, x.detected_type, x.malware_status,
       x.exif_removed, x.approved_for_ai, x.derived_storage_reference,
       x.pii_findings
from complaint_attachments a
left join attachment_analysis x on x.attachment_id = a.id
order by a.created_at desc;

-- Audit and Flyway state
select id, http_method, request_path, actor, status_code, duration_ms, created_at
from audit_logs
order by created_at desc;

select installed_rank, version, description, success, installed_on
from flyway_schema_history
order by installed_rank;

-- GIS spatial data mart
select id, source_name, source_type, phase, jurisdiction_code, status,
       collected_at, data_effective_on, content_hash, updated_at
from spatial_source_registry
order by phase, source_name;

select s.source_name,
       (select count(*) from spatial_admin_boundaries x where x.source_id = s.id) as admin_boundaries,
       (select count(*) from spatial_address_points x where x.source_id = s.id) as address_points,
       (select count(*) from spatial_road_segments x where x.source_id = s.id) as road_segments,
       (select count(*) from spatial_facilities x where x.source_id = s.id) as facilities,
       (select count(*) from spatial_parking_restrictions x where x.source_id = s.id) as parking_restrictions
from spatial_source_registry s
order by s.phase, s.source_name;

select id, complaint_id, complaint_issue_id, normalized_location_text,
       status, blocker, created_at
from spatial_location_resolution_runs
order by created_at desc;

select run_id, candidate_type, label, address, source_layer, source_record_id,
       confidence, confidence_score, needs_human_confirmation,
       latitude, longitude, evidence_summary
from spatial_location_candidates
order by run_id, confidence_score desc;
