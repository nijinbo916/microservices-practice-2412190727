# syntax=docker/dockerfile:1

# =====================================================================
# gateway-service 镜像：多阶段构建（与其余微服务同一模板）
#   build   阶段：Maven + JDK 25 编译打包
#   runtime 阶段：仅 JRE 25 + 应用 JAR，非 root 用户运行
#
# 依赖单独一层（dependency:go-offline）：
#   改 Java 代码只重跑后面的 package，不会重新下载依赖，
#   这是第 05 周踩过「每次构建都重下 200MB」之后固定的写法。
# =====================================================================
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /workspace

# 依赖下载走国内镜像（容器内直连 Maven Central 只有 4~30 KB/s）
COPY docker/maven-settings.xml /root/.m2/settings.xml

COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:25-jre AS runtime

WORKDIR /app

RUN groupadd --system --gid 1001 appuser \
 && useradd --system --uid 1001 --gid appuser --home-dir /app appuser

COPY --from=build --chown=appuser:appuser \
     /workspace/target/gateway-service.jar /app/app.jar

USER appuser

EXPOSE 8080

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
