# Description:
#   ErrorProne refactorings and static analysis for Dagger

load("//:build_defs.bzl", "HILT_RUNTIME_JVM_TARGET")

package(default_visibility = ["//:src"])

java_library(
    name = "errorprone",
    srcs = glob(["*.java"]),
    javacopts = HILT_RUNTIME_JVM_TARGET,
    deps = [
        "//java/dagger:core",
        "//third_party/java/error_prone:check_api",
        "//third_party/java/guava/collect",
    ],
)
