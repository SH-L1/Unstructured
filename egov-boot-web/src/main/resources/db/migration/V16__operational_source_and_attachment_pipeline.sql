alter table source_registry add column collection_interval_minutes integer not null default 1440;
alter table source_registry add column last_successful_sync_at timestamp;
alter table source_registry add column last_failure_at timestamp;
alter table source_registry add column last_failure_reason text;
alter table source_registry add column next_sync_at timestamp;
alter table source_registry add column stale_after timestamp;
alter table source_registry add column last_content_hash varchar(128);

alter table knowledge_documents add column source_registry_id bigint;
alter table knowledge_documents
    add constraint fk_knowledge_documents_source_registry
    foreign key (source_registry_id) references source_registry (id);

alter table processing_jobs add column payload_reference varchar(500);
alter table attachment_analysis add column derived_storage_reference varchar(500);

create index idx_source_registry_due on source_registry (status, next_sync_at);
create index idx_knowledge_documents_source_registry on knowledge_documents (source_registry_id);
create index idx_processing_jobs_type_status_created_at on processing_jobs (job_type, status, created_at);
