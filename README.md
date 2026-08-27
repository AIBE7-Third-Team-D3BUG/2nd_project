# 2nd_project
**2차 프로젝트 저장소 리뷰** 

| 참고영역 | 참고사항  | 적용 결론 |
| --- | --- | --- |
| 프로젝트 일정 | 기획·디자인·개발·안정화 단계 구분 | Spring Boot 기반 개발, 우선 구현*후순위 구현 구분 |
| 협업 프로세스 | 동행 스크럼, 기술 로그, 코드 리뷰 | Notion + GitHub + Figma 이용 |
| ERD 설명 | 전체 ERD와 핵심 테이블 분리 | README + ERD + OpenAPI + ADR + 테스트·배포 문서 작성 |
| 아키텍처 문서 | 프론트·백엔드·인프라 구성도 |  
| 상세 기능 명세 | 화면별 기능과 정책 |  
| 트러블슈팅 | 상황·원인·해결 구조 | 수시 작성 |

**방향성**  - 긴급 업무 매칭 플랫폼: 실시간 가용성과 납기 신뢰도를 기반으로, 24시간 이내 처리가 필요한 디지털 업무를 프리랜서와 매칭하는 플랫폼
초기 범위는 ppt*디자인 개선 및 수정 / 번역 / 개발 오류,서버점검 등 원격작업이 가능한 단기 작업으로 제한. 

**벤치마킹 사이트 및 요소 기술**

| 사이트 | 벤치마킹 요소 | 프로젝트 적용 | 구현 기술 |
| --- | --- | --- | --- |
| 크몽 | 전문가 프로필, 서비스 패키지, 포트폴리오, 채팅, 안전결제, 리뷰 | 전문가 상세 페이지, 포트폴리오, 거래 완료 후 리뷰 | PostgreSQL 검색, Object Storage, WebSocket, PG 결제 |
| 위시켓 | 프로젝트 등록, 지원자 모집, 견적 비교, 계약, 대금보호 | 긴급 업무 등록, 프리랜서 입찰, 계약 상태 관리 | 입찰 테이블, 상태 머신, 전자계약, 에스크로 모의 구현 |
| 숨고 | 요청서 작성, 전문가 견적 제안, 빠른 상담 | 의뢰서를 전문가에게 발송하고 제한 시간 내 견적 수신 | 매칭 알고리즘, 실시간 알림, 동적 요청 폼 |
| Fiverr | 서비스 패키지, 옵션별 가격, 빠른 납기 추가요금 | 기본 가격·긴급 할증·추가 수정 옵션 | 가격 정책 엔진, 옵션 테이블 |
| Upwork | 프로젝트 제안, 마일스톤, 계약, 작업 기록 | 단계별 납품과 대금 승인 | 마일스톤, 작업 로그, 계약 이력 |
| 당근 | 가까운 일거리, 빠른 지원, 채팅 | 현재 작업 가능한 프리랜서 우선 노출 응답률·납기 준수율·거래 횟수를 종합한 신뢰점수 | 가용 상태, 위치 또는 시간 기반 필터 |

**툴 리스트 업** 

| 영역 | 도구 | 용도 |
| --- | --- | --- |
| 문서·Wiki | Notion | 요구사항, 회의록, 정책, 회고, 의사결정 기록 |
| UI/UX | Figma | 와이어프레임, 디자인 시스템, 프로토타입 |
| 형상관리 | GitHub | Git 저장소, 브랜치, PR, 코드리뷰 |
| DB 모델링 | supabase | ERD, 관계와 제약조건 시각화 |
| API 명세 | Swagger/OpenAPI | 백엔드 API 명세 자동화 |
| 실시간 소통 | Discord 또는 Slack | 빠른 질문, 장애·배포 알림 |
| AI 제품 기능 | OpenAI API | 의뢰서 구조화, 요약, 안전성 검사 |

## 현재 구현 범위

- 회원가입·로그인과 프로필 관리
- 업무 등록·수정·삭제, 재화 예약·반환·정산
- 업무 지원, 작업자 선택, 진행·제출·수정 요청·완료 승인
- 작업자 선택 시 업무별 1:1 채팅방 자동 생성
- 채팅 메시지 읽음 처리와 비공개 첨부 파일 다운로드

## 프로젝트 문서

- [프로젝트 제안서](PROJECT_PROPOSAL.md)
- [요구사항 정의서](REQUIREMENTS.md)
- [작업 분해 구조](WBS.md)
- [데이터베이스 스키마 설계서](DB_SCHEMA.md)

## 채팅 기능 실행 조건

채팅방은 의뢰인이 지원자를 작업자로 선택할 때 자동으로 생성됩니다. 의뢰인과 선택된 작업자만 채팅방, 메시지, 첨부 파일에 접근할 수 있습니다.

`db` 프로필로 애플리케이션을 실행하면 Flyway가 V1~V13 이력을 검증하고 아직 적용되지 않은 마이그레이션만 순서대로 적용합니다. 현재 버전에는 채팅 스키마 정렬, 품 원장 보존, 채팅방 나가기, 관리자 감사 로그, 메시지 관리 및 시스템 알림 변경이 포함됩니다. 이미 운영 DB에 적용된 마이그레이션 파일은 수정하지 않습니다.

Supabase Storage는 다음 조건으로 설정합니다.

- 버킷 이름: `SUPABASE_STORAGE_BUCKET` 값과 동일하게 설정
- 공개 버킷: 비활성화
- 최대 파일 크기: 6MB 이상
- 허용 MIME 유형: `image/*, text/*, application/pdf, application/zip, application/x-zip-compressed, application/vnd.openxmlformats-officedocument.wordprocessingml.document, application/vnd.openxmlformats-officedocument.presentationml.presentation, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, application/x-hwp, application/haansofthwp`
- `SUPABASE_SERVICE_ROLE_KEY`는 서버 환경변수로만 관리하고 브라우저와 Git 저장소에 노출하지 않음

첨부 파일은 공개 URL을 저장하지 않습니다. 서버가 채팅 참여자 여부를 검사한 후 5분 동안 유효한 서명 URL을 발급합니다.

## 로컬 실행 및 테스트

`.env.sample`을 참고해 환경변수를 설정하고 Java 17로 실행합니다.

```powershell
.\gradlew.bat bootRun
```

Supabase 연결 없이 자동화 테스트를 실행하려면 다음 명령을 사용합니다.

```powershell
.\gradlew.bat test
```

Supabase 통합 테스트는 별도로 실행합니다.

```powershell
.\gradlew.bat supabaseTest
```
