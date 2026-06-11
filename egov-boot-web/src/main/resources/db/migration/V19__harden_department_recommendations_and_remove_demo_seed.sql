alter table department_tasks
    add column if not exists recommendation_score integer;

alter table department_tasks
    add column if not exists recommendation_source varchar(60);

update department_tasks
set recommendation_score = coalesce(
        case
            when recommendation_reason like 'RULE_BASED:%' then 100
            when recommendation_reason like 'FALLBACK:%' then 70
            when recommendation_reason like 'AI_MODEL:%score=80%' then 80
            when recommendation_reason like 'AI_MODEL:%score=60%' then 60
            when recommendation_reason like 'AI_MODEL:%score=40%' then 40
            when recommendation_reason like 'AI_MODEL:%' then 60
            else 0
        end
    ),
    recommendation_source = coalesce(
        recommendation_source,
        case
            when recommendation_reason like 'RULE_BASED:%' then 'ASSIGNMENT_RULE'
            when recommendation_reason like 'FALLBACK:%' then 'DEFAULT_ROUTING'
            when recommendation_reason like 'AI_MODEL:%' then 'AI_MODEL'
            else 'UNSPECIFIED'
        end
    );

alter table department_tasks
    alter column recommendation_score set not null;

alter table department_tasks
    alter column recommendation_source set not null;

alter table department_tasks
    drop constraint if exists ck_department_tasks_recommendation_score;

alter table department_tasks
    add constraint ck_department_tasks_recommendation_score
        check (recommendation_score between 0 and 100);

alter table department_tasks
    drop constraint if exists ck_department_tasks_status;

alter table department_tasks
    add constraint ck_department_tasks_status
        check (status in ('CANDIDATE', 'HUMAN_SELECTED', 'VERIFIED', 'REJECTED'));

alter table department_tasks
    drop constraint if exists ck_department_tasks_recommendation_source;

alter table department_tasks
    add constraint ck_department_tasks_recommendation_source
        check (recommendation_source in ('ASSIGNMENT_RULE', 'DEFAULT_ROUTING', 'AI_MODEL', 'UNSPECIFIED'));

delete from assignment_rules
where synthetic_demo = true
   or jurisdiction_code = 'SYNTHETIC_DEMO_PILOT'
   or rule_text like 'SYNTHETIC_DEMO%';

delete from organization_units
where synthetic_demo = true
   or jurisdiction_code = 'SYNTHETIC_DEMO_PILOT'
   or code like 'SYNTHETIC_DEMO_%';
