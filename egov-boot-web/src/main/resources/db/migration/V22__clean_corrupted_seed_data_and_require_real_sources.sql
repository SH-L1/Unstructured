update departments
set name = v.name,
    description = v.description,
    active = true,
    updated_at = current_timestamp
from (values
    ('SAFETY_CONTROL', '안전총괄과', '재난, 안전, 위험물 및 긴급 위험 관리'),
    ('RESOURCE_RECYCLING', '자원순환과', '폐기물, 생활쓰레기, 재활용, 무단투기 민원 처리'),
    ('ROAD', '도로과', '도로, 보도, 포트홀, 도로시설물, 가로등 정비'),
    ('TRAFFIC', '교통행정과', '불법 주정차, 교통시설, 교통표지 및 신호 민원 처리'),
    ('CIVIL_AFFAIRS', '민원지적과', '일반 민원 접수, 지적, 종합 안내'),
    ('WATER_SEWER', '상하수도과', '상수도, 하수도, 배수, 맨홀 및 누수 민원 처리'),
    ('BUILDING_HOUSING', '건축과', '건축, 건축물 안전, 주거환경 민원 처리'),
    ('PARK_GREEN', '공원녹지과', '공원, 녹지, 가로수, 놀이터 시설 민원 처리'),
    ('HEALTH_SANITATION', '위생과', '공중위생, 공중화장실, 식품위생 민원 처리'),
    ('ANIMAL_LIVESTOCK', '축산과', '동물, 가축, 반려동물 및 축산 관련 민원 처리'),
    ('URBAN_MANAGEMENT', '도시계획과', '도시계획, 개발행위, 광고물 관련 민원 처리'),
    ('WELFARE', '사회복지과', '복지, 장애인, 노인, 취약계층 관련 민원 처리'),
    ('ENVIRONMENT', '기후변화대책과', '환경오염, 악취, 소음, 대기 관련 민원 처리')
) as v(code, name, description)
where departments.code = v.code;

delete from knowledge_document_chunks
where knowledge_document_id in (
    select id
    from knowledge_documents
    where content_hash in (
        '321bd1b92177487438a65671027c9a5cb95c99ec419a6ecca32e7768fded751f',
        '58ccfc6279ef4a4eb26f7bcada15e4fc1f42046e155a1de0079b9810d167c7e2',
        '658423d7fb545cb81bd432cf6aff7d028d6f2354de55080a94fa1cded10aedcc',
        '19b316292e972f54a577fd1c717203f448cdc58b959c1fe367e56dcd804e3f20',
        'd52f7ee3105e671161b888affd9484e491345f1d6e52c598a4dd3c019b357987',
        '9d4ef7f0af7594415e1846595160e4b7967f1fb1cc4deb2b8f79e392af689935',
        '0d0387faa32efbc7eab9eaa2058308e1476c698b188c4c7f1607b9eeb62e06d4',
        '01ce84ac0f1412e57d5a997a8f6322da0ea11ce522b669f43b9201b6c63288be',
        'ef7ec20c083af073c5c4cdda0e78bd3d28a9150b36d725cf53ae34b94df1d7fc',
        '75162db9bd823c65a14676b2180693a0ad83ed0d0a6dc2e0f5e42f3b18ed1612'
    )
);

delete from knowledge_purpose
where knowledge_document_id in (
    select id
    from knowledge_documents
    where content_hash in (
        '321bd1b92177487438a65671027c9a5cb95c99ec419a6ecca32e7768fded751f',
        '58ccfc6279ef4a4eb26f7bcada15e4fc1f42046e155a1de0079b9810d167c7e2',
        '658423d7fb545cb81bd432cf6aff7d028d6f2354de55080a94fa1cded10aedcc',
        '19b316292e972f54a577fd1c717203f448cdc58b959c1fe367e56dcd804e3f20',
        'd52f7ee3105e671161b888affd9484e491345f1d6e52c598a4dd3c019b357987',
        '9d4ef7f0af7594415e1846595160e4b7967f1fb1cc4deb2b8f79e392af689935',
        '0d0387faa32efbc7eab9eaa2058308e1476c698b188c4c7f1607b9eeb62e06d4',
        '01ce84ac0f1412e57d5a997a8f6322da0ea11ce522b669f43b9201b6c63288be',
        'ef7ec20c083af073c5c4cdda0e78bd3d28a9150b36d725cf53ae34b94df1d7fc',
        '75162db9bd823c65a14676b2180693a0ad83ed0d0a6dc2e0f5e42f3b18ed1612'
    )
);

delete from knowledge_documents
where content_hash in (
    '321bd1b92177487438a65671027c9a5cb95c99ec419a6ecca32e7768fded751f',
    '58ccfc6279ef4a4eb26f7bcada15e4fc1f42046e155a1de0079b9810d167c7e2',
    '658423d7fb545cb81bd432cf6aff7d028d6f2354de55080a94fa1cded10aedcc',
    '19b316292e972f54a577fd1c717203f448cdc58b959c1fe367e56dcd804e3f20',
    'd52f7ee3105e671161b888affd9484e491345f1d6e52c598a4dd3c019b357987',
    '9d4ef7f0af7594415e1846595160e4b7967f1fb1cc4deb2b8f79e392af689935',
    '0d0387faa32efbc7eab9eaa2058308e1476c698b188c4c7f1607b9eeb62e06d4',
    '01ce84ac0f1412e57d5a997a8f6322da0ea11ce522b669f43b9201b6c63288be',
    'ef7ec20c083af073c5c4cdda0e78bd3d28a9150b36d725cf53ae34b94df1d7fc',
    '75162db9bd823c65a14676b2180693a0ad83ed0d0a6dc2e0f5e42f3b18ed1612'
);
