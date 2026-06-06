create table workflow_audit_events (
    id uuid not null,
    entity_type varchar(60) not null,
    entity_id varchar(200) not null,
    action varchar(80) not null,
    actor varchar(100) not null,
    actor_role varchar(40) not null,
    before_value text,
    after_value text not null,
    idempotency_key varchar(200),
    created_at timestamp not null,
    constraint pk_workflow_audit_events primary key (id)
);

create index idx_workflow_audit_events_entity
    on workflow_audit_events (entity_type, entity_id, created_at);
