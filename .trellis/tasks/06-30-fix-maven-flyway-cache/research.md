# Research: Maven Flyway Cache And Lombok Diagnostics

## Confirmed Facts

- `km-backend/pom.xml:86` declares `org.flywaydb:flyway-core`; no explicit version is set in the module.
- `pom.xml:14` uses `spring-boot-starter-parent` version `2.6.4`, which manages dependency versions for Spring Boot modules.
- `km-backend/pom.xml:90` declares `org.projectlombok:lombok` as an optional dependency.
- `pom.xml:29` sets `lombok.version` to `1.18.36`.
- `km-backend/src/main/java/com/km/dto/ai/AiApiResponse.java:5` uses Lombok `@Data`, so `getData()`, `getCode()`, and `getMessage()` are generated methods.
- `km-backend/src/main/java/com/km/client/KmAiClient.java:13` uses Lombok `@Slf4j`, so `log` is a generated field.
- Backend source uses Lombok annotations in many DTO, VO, entity, service, and config classes, so fixing one caller by adding hand-written accessors would be inconsistent with the project pattern.

## Planning Implication

- First repair the local Maven cache and force dependency resolution. A stale failed dependency download can make IDE/compiler diagnostics noisy.
- Then run `mvn -pl km-backend -am compile -U` to distinguish a build-reproducible source error from an IDE annotation-processing issue.
- If Maven compile succeeds while the editor still reports `KmAiClient` Lombok errors, the likely fix is IDE/Lombok annotation processing configuration outside application source.
- If Maven compile reproduces the Lombok errors, expand this task or create a focused child task before changing Java source or Maven compiler configuration.
