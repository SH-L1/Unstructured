drop index if exists idx_rag_contexts_complaint_id_score;
drop index if exists idx_rag_contexts_official_draft_id_score;

alter table rag_contexts drop column score;

create index idx_rag_contexts_complaint_id on rag_contexts (complaint_id);
create index idx_rag_contexts_official_draft_id on rag_contexts (official_draft_id);
