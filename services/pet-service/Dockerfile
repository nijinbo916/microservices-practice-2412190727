# syntax=docker/dockerfile:1

# =====================================================================
# pet-service 镜像：多阶段构建（与 monolith 同一模板）
#   build   阶段：Maven + JDK 25 编译打包
#   runtime 阶段：仅 JRE 25 + 应用 JAR，非 root 用户运行
# =====================================================================
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /workspace

# 依赖下载走国内镜像（容器内直连 Maven Central 速度过慢）
COPY docker/maven-settings.xml /root/.m2/settings.xml

# 本地种子包（gitignore，仅存在于开发机）：有则离线解压加速，无则回退在线下载，均不影响构建正确性
COPY docker/ /tmp/docker/
RUN if [ -f /tmp/docker/m2-local.tgz ]; then \
      tar xzf /tmp/docker/m2-local.tgz -C /root/.m2 && rm -rf /tmp/docker; \
    fi

COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:25-jre AS runtime

WORKDIR /app

RUN groupadd --system --gid 1001 appuser \
 && useradd --system --uid 1001 --gid appuser --home-dir /app appuser

COPY --from=build --chown=appuser:appuser \
     /workspace/target/pet-service.jar /app/app.jar

USER appuser

EXPOSE 8080

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
