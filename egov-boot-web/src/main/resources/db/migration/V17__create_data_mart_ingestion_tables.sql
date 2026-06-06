create table data_mart_ingestion_runs (
    id uuid not null,
    source_registry_id bigint,
    source_type varchar(40) not null,
    source_name varchar(200) not null,
    purpose varchar(40) not null,
    query_text text,
    status varchar(40) not null,
    started_at timestamp not null,
    completed_at timestamp,
    record_count integer not null default 0,
    failure_reason text,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint pk_data_mart_ingestion_runs primary key (id),
    constraint fk_data_mart_ingestion_runs_source foreign key (source_registry_id) references source_registry (id)
);

create table data_mart_raw_records (
    id uuid not null,
    ingestion_run_id uuid,
    source_registry_id bigint not null,
    source_type varchar(40) not null,
    external_id varchar(300),
    source_name varchar(200) not null,
    source_url varchar(500),
    query_text text,
    request_fingerprint varchar(128),
    response_content_type varchar(120),
    raw_payload text not null,
    raw_payload_hash varchar(128) not null,
    fetched_at timestamp not null,
    created_at timestamp not null,
    constraint pk_data_mart_raw_records primary key (id),
    constraint fk_data_mart_raw_records_run foreign key (ingestion_run_id) references data_mart_ingestion_runs (id),
    constraint fk_data_mart_raw_records_source foreign key (source_registry_id) references source_registry (id),
    constraint uk_data_mart_raw_records_hash unique (source_registry_id, external_id, raw_payload_hash)
);

create table data_mart_normalized_records (
    id uuid not null,
    raw_record_id uuid not null,
    knowledge_document_id bigint,
    legal_document_version_id bigint,
    record_type varchar(40) not null,
    title varchar(500) not null,
    content text not null,
    metadata_json text not null,
    purpose varchar(40) not null,
    verification_status varchar(40) not null,
    legal_evidence_allowed boolean not null,
    jurisdiction_code varchar(80),
    effective_from date,
    effective_to date,
    content_hash varchar(128) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint pk_data_mart_normalized_records primary key (id),
    constraint fk_data_mart_normalized_raw foreign key (raw_record_id) references data_mart_raw_records (id),
    constraint fk_data_mart_normalized_knowledge foreign key (knowledge_document_id) references knowledge_documents (id),
    constraint fk_data_mart_normalized_legal_version foreign key (legal_document_version_id) references legal_document_versions (id),
    constraint uk_data_mart_normalized_hash unique (raw_record_id, content_hash)
);

create table data_mart_load_errors (
    id uuid not null,
    ingestion_run_id uuid,
    raw_record_id uuid,
    source_type varchar(40) not null,
    error_stage varchar(40) not null,
    error_code varchar(80) not null,
    error_message text not null,
    retryable boolean not null,
    created_at timestamp not null,
    constraint pk_data_mart_load_errors primary key (id),
    constraint fk_data_mart_load_errors_run foreign key (ingestion_run_id) references data_mart_ingestion_runs (id),
    constraint fk_data_mart_load_errors_raw foreign key (raw_record_id) references data_mart_raw_records (id)
);

create index idx_data_mart_ingestion_runs_source_status on data_mart_ingestion_runs (source_registry_id, status, started_at);
create index idx_data_mart_ingestion_runs_type_status on data_mart_ingestion_runs (source_type, status, started_at);
create index idx_data_mart_raw_records_source_fetched on data_mart_raw_records (source_registry_id, fetched_at);
create index idx_data_mart_raw_records_hash on data_mart_raw_records (raw_payload_hash);
create index idx_data_mart_normalized_purpose on data_mart_normalized_records (purpose, verification_status, legal_evidence_allowed);
create index idx_data_mart_normalized_knowledge on data_mart_normalized_records (knowledge_document_id);
create index idx_data_mart_normalized_legal_version on data_mart_normalized_records (legal_document_version_id);
create index idx_data_mart_load_errors_run on data_mart_load_errors (ingestion_run_id, created_at);
