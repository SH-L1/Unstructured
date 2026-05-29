# 프로젝트 세팅 요약

이 문서는 현재 저장소의 최종 개발 기준과 실행 세팅을 빠르게 확인하기 위한 요약이다.

## 최종 기준

전자정부프레임워크 사용이 필수이므로 메인 백엔드는 `egov-boot-web`이다.

```text
C:\Users\user\Documents\GitHub\Unstructured\egov-boot-web
```

`egov-boot-web`은 VS Code Extension `eGovFrame Initializr 5.0.5`로 생성한 eGovFrame Boot Web 프로젝트이며, `pom.xml`에 eGovFrame 5.0 실행환경 의존성이 포함되어 있다.

기존 `backend`는 Spring Initializr 기반 Gradle 프로젝트였으나, 전자정부프레임워크 필수 조건을 만족하기 위해 더 이상 메인 백엔드로 보지 않는다. 기존 `backend`의 민원 API, 도메인, 서비스 코드는 `egov-boot-web`의 `egovframework.example.complaint` 패키지로 이식했다.

## 디렉터리 기준

```text
egov-boot-web/   # 메인 백엔드: eGovFrame 5.0 기반 Maven 프로젝트
backend/         # 이전 Spring Boot/Gradle 백엔드. 참고용 또는 추후 정리 대상
```

## eGovFrame 적용 상태

`egov-boot-web/pom.xml` 기준:

```xml
<parent>
    <groupId>org.egovframe.boot</groupId>
    <artifactId>egovframe-boot-starter-parent</artifactId>
    <version>5.0.0</version>
</parent>
```

포함된 eGovFrame 실행환경 모듈:

- `org.egovframe.rte:org.egovframe.rte.ptl.mvc`
- `org.egovframe.rte:org.egovframe.rte.psl.dataaccess`
- `org.egovframe.rte:org.egovframe.rte.fdl.idgnr`
- `org.egovframe.rte:org.egovframe.rte.fdl.property`

추가된 프로젝트 의존성:

- `spring-boot-starter-data-jpa`
- `spring-boot-starter-security`
- `spring-boot-starter-actuator`
- `postgresql`

## Java 및 Maven

Java 기준:

```text
Java 17
```

Maven 설치 경로:

```text
C:\maven\apache-maven-3.9.16\bin
```

사용자 PATH에도 위 경로를 추가했다. 이미 열려 있던 터미널에서는 PATH 갱신이 바로 반영되지 않을 수 있으므로, `mvn`이 인식되지 않으면 터미널을 새로 열면 된다.

확인 명령:

```powershell
mvn -v
```

## 실행 포트

`egov-boot-web/src/main/resources/application.properties` 기준:

```properties
server.port=8081
```

기존 8080 포트 충돌 가능성을 피하기 위해 8081을 사용한다.

## 데이터베이스

PostgreSQL 기준:

```text
Host: localhost
Port: 5432
Database: complaintdb
User: complaint_user
Password: complaint_pass
```

애플리케이션 설정:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/complaintdb
spring.datasource.username=complaint_user
spring.datasource.password=complaint_pass
spring.jpa.hibernate.ddl-auto=update
```

eGovFrame 샘플의 HSQLDB 설정은 PostgreSQL 설정으로 교체했다. 샘플 파일 `db/sampledb.sql`은 더 이상 메인 업무 DB 기준이 아니다.

## 구현 위치

민원 기능 구현 위치:

```text
egov-boot-web/src/main/java/egovframework/example/complaint
```

구성:

- `api`: REST API Controller, DTO, 예외 처리
- `domain`: 민원 Entity, 상태 Enum
- `repository`: JPA Repository
- `service`: 민원 접수/조회/분석 서비스
- `config`: 보안 설정

## API

현재 구현된 API:

```text
POST /api/complaints
GET  /api/complaints?status=&department=&urgency=&page=&size=
GET  /api/complaints/{id}
POST /api/complaints/{id}/attachments
GET  /api/complaints/{id}/attachments
PATCH /api/complaints/{id}/status
GET  /api/complaints/{id}/analysis
GET  /api/complaints/{id}/draft
PUT  /api/complaints/{id}/draft
GET  /api/complaints/{id}/rag-contexts
GET  /api/complaints/{id}/geojson
GET  /api/departments
GET  /actuator/health
```

Mock 분석/RAG/초안 로직을 사용하며, 실제 AI/RAG/AWS 연동은 다음 단계에서 교체한다.

## 실행 명령

```powershell
cd C:\Users\user\Documents\GitHub\Unstructured\egov-boot-web
mvn test
mvn spring-boot:run
```

상태 확인:

```powershell
Invoke-WebRequest -Uri http://localhost:8081/actuator/health -UseBasicParsing
```

## 확인 완료

- Maven 설치 및 PATH 추가
- `egov-boot-web` Maven 테스트 통과
- PostgreSQL 컨테이너 실행 확인
- 8081 포트 애플리케이션 실행 확인
- 민원 등록 API 수동 호출 확인
- 민원 분석 결과 조회 API 수동 호출 확인
- 필터링/상태 변경/부서/RAG/초안 수정/첨부파일 API 컴파일 확인

## 다음 개발 순서

1. 기존 `backend`를 참고용으로 둘지 삭제할지 결정
2. 첨부파일 다운로드/삭제 API 추가
3. 로컬 파일 저장소를 S3 등 운영 저장소 구현으로 교체
4. Mock 분석/RAG/초안 서비스를 실제 AI/RAG 연동 구현으로 교체
5. 프론트엔드 대시보드를 `egov-boot-web` API에 연결
6. 운영용 인증/권한/감사 로그/배포 설정 보강

## 주의사항

- 최종 산출물과 발표에서는 `egov-boot-web`을 기준 백엔드로 설명한다.
- `backend`는 eGovFrame 실행환경을 포함하지 않으므로 메인 백엔드로 설명하면 안 된다.
- API Key, DB 비밀번호, AWS Access Key 등은 코드와 Git에 직접 저장하지 않는다.
- 실제 LLM/Vector DB 호출은 비용이 발생하므로 개발 초반에는 Mock 구현을 유지한다.
