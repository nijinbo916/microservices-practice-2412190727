# syntax=docker/dockerfile:1

# =====================================================================
# 萌宠诊所 · 宠物医院管理系统 —— 单体应用镜像
#
# 多阶段构建：
#   build   阶段：Maven + JDK 25，负责编译打包，产出可执行 JAR
#   runtime 阶段：仅 JRE 25，只保留 JAR 与运行时依赖
#
# 好处：最终镜像里没有 Maven、没有 JDK、没有源码，
#       镜像体积更小、攻击面更小；构建过程也不依赖宿主机装 Java/Maven。
# =====================================================================


# ---------------------------------------------------------------------
# 阶段一：build —— 用 Maven 镜像编译打包
# ---------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /workspace

# 依赖下载走国内镜像（容器内直连 Maven Central 实测只有几十 KB/s）
COPY docker/maven-settings.xml /root/.m2/settings.xml

# 先只拷贝 pom.xml 并预下载依赖。
# 这样只要依赖没变，改动源码时这一层可以直接命中缓存，不必重新下载依赖。
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

# 再拷贝源码执行打包。测试在第 04 周已本地验证，构建镜像时跳过以缩短时间。
COPY src ./src
RUN mvn -B -DskipTests clean package


# ---------------------------------------------------------------------
# 阶段二：runtime —— 只用 JRE 运行
# ---------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime

WORKDIR /app

# 创建非 root 用户运行应用，避免容器内进程拥有 root 权限
RUN groupadd --system --gid 1001 appuser \
 && useradd --system --uid 1001 --gid appuser --home-dir /app appuser

# 只从构建阶段拿最终产物，不携带任何构建工具
COPY --from=build --chown=appuser:appuser \
     /workspace/target/monolith-0.0.1-SNAPSHOT.jar /app/app.jar

USER appuser

EXPOSE 8080

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"

# 用 exec 让 java 成为 1 号进程，能正确收到 docker stop 的 SIGTERM
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
