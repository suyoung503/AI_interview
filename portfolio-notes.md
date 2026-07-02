# 트러블슈팅 기록

## Docker 베이스 이미지 문제

### 오류 내용
```
ERROR [internal] load metadata for docker.io/library/eclipse-temurin:17-jre-alpine
```
```
Error response from daemon: failed to resolve reference "docker.io/library/openjdk:17-slim": not found
```

### 원인
1. `eclipse-temurin:17-jre-alpine` → Docker Hub에서 메타데이터를 가져오지 못하는 네트워크 문제
2. `openjdk:17-slim` → openjdk 공식 이미지가 deprecated 되어 삭제됨

### 시도한 대안
| 이미지 | 결과 | 이유 |
|---|---|---|
| `eclipse-temurin:17-jre-alpine` | 실패 | Docker Hub 메타데이터 로딩 실패 |
| `openjdk:17-slim` | 실패 | 공식 이미지 deprecated (삭제됨) |
| `amazoncorretto:17` | 성공 | Amazon이 관리하는 OpenJDK 배포판, 안정적으로 유지됨 |

### 해결
`Dockerfile` 베이스 이미지를 `amazoncorretto:17` 로 변경

```dockerfile
# 변경 전
FROM eclipse-temurin:17-jre-alpine

# 변경 후
FROM amazoncorretto:17
```

### 배운 점
- Docker 공식 `openjdk` 이미지는 deprecated 되었으므로 사용하면 안 됨
- Java Docker 이미지 선택지: `eclipse-temurin`, `amazoncorretto`, `microsoft/openjdk` 등 벤더별 배포판 사용 권장
- `alpine` 태그는 용량이 가볍지만 네트워크 환경에 따라 pull이 안 될 수 있음
