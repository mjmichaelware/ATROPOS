# Produces both the JVM image and the optional native-image target from the
# same source checkout. The default target is the JVM image.
FROM gradle:8.4-jdk17 AS engine-build
WORKDIR /workspace
COPY . .
RUN gradle --no-daemon clean jar --max-workers=1

FROM ghcr.io/graalvm/native-image-community:17 AS native-build
COPY --from=engine-build /workspace/build/libs/ATROPOS.jar /tmp/ATROPOS.jar
RUN native-image --no-fallback -H:+ReportExceptionStackTraces -jar /tmp/ATROPOS.jar /out/atropos-native

FROM eclipse-temurin:17-jre AS jvm
WORKDIR /app
COPY --from=engine-build /workspace/build/libs/ATROPOS.jar /app/ATROPOS.jar
STOPSIGNAL SIGTERM
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD java -jar /app/ATROPOS.jar --health >/dev/null 2>&1 || exit 1
ENTRYPOINT ["java", "-jar", "/app/ATROPOS.jar"]

FROM gcr.io/distroless/base-debian12 AS native
WORKDIR /app
COPY --from=native-build /out/atropos-native /app/atropos
STOPSIGNAL SIGTERM
ENTRYPOINT ["/app/atropos"]
