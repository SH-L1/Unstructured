update complaint_analysis
set geo_json = null
where geo_json is not null;
