alter table draft_claims add column source_document_ids text;

update draft_claims
set source_document_ids = ''
where source_document_ids is null;

alter table draft_claims alter column source_document_ids set not null;
