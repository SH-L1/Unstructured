insert into organization_units (
    code, name, jurisdiction_code, synthetic_demo, active, valid_from, valid_to, created_at, updated_at
) values
    ('SYNTHETIC_DEMO_RESOURCE_RECYCLING', 'Synthetic Demo Resource Recycling', 'SYNTHETIC_DEMO_PILOT', true, true, current_date, null, current_timestamp, current_timestamp),
    ('SYNTHETIC_DEMO_ROAD', 'Synthetic Demo Road Management', 'SYNTHETIC_DEMO_PILOT', true, true, current_date, null, current_timestamp, current_timestamp),
    ('SYNTHETIC_DEMO_TRAFFIC', 'Synthetic Demo Traffic Administration', 'SYNTHETIC_DEMO_PILOT', true, true, current_date, null, current_timestamp, current_timestamp);

insert into assignment_rules (
    organization_unit_id, complaint_type, jurisdiction_code, rule_text, priority,
    synthetic_demo, active, valid_from, valid_to, created_at, updated_at
) values
    ((select id from organization_units where code = 'SYNTHETIC_DEMO_RESOURCE_RECYCLING'),
     'ILLEGAL_DUMPING', 'SYNTHETIC_DEMO_PILOT', 'SYNTHETIC_DEMO candidate rule; human confirmation required',
     100, true, true, current_date, null, current_timestamp, current_timestamp),
    ((select id from organization_units where code = 'SYNTHETIC_DEMO_ROAD'),
     'ROAD_DAMAGE', 'SYNTHETIC_DEMO_PILOT', 'SYNTHETIC_DEMO candidate rule; human confirmation required',
     100, true, true, current_date, null, current_timestamp, current_timestamp),
    ((select id from organization_units where code = 'SYNTHETIC_DEMO_TRAFFIC'),
     'ILLEGAL_PARKING', 'SYNTHETIC_DEMO_PILOT', 'SYNTHETIC_DEMO candidate rule; human confirmation required',
     100, true, true, current_date, null, current_timestamp, current_timestamp);
