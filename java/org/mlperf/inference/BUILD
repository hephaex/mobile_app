# Copyright 2019 The MLPerf Authors. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ==============================================================================
load("@build_bazel_rules_android//android:rules.bzl", "android_binary", "android_library")
load("@org_tensorflow//tensorflow/lite:build_def.bzl", "tflite_jni_binary")
load("//cpp/proto:gen_binary_proto.bzl", "text_to_bin")

package(
    default_visibility = ["//visibility:public"],
    licenses = ["notice"],  # Apache 2.0
)

java_lite_proto_library(
    name = "mlperf_task_java_proto_lite",
    deps = ["//cpp/proto:mlperf_task_proto"],
)

text_to_bin(
    name = "tasks.pb",
    src = "assets/tasks.pbtxt",
    out = "res/raw/tasks_pb",
    proto_deps = ["//cpp/proto:mlperf_task_java_proto"],
    proto_message = "org.mlperf.proto.MLPerfConfig",
)

android_library(
    name = "evaluation_app_lib",
    srcs = [
        "MLPerfEvaluation.java",
        "MLPerfTasks.java",
        "ModelsPreference.java",
        "ResultHolder.java",
        "ResultsAdapter.java",
        "RunMLPerfWorker.java",
        "SettingsActivity.java",
    ],
    manifest = "AndroidManifest.xml",
    resource_files = [":tasks.pb"] + glob(["res/**"]),
    deps = [
        ":jni_wrapper",
        ":mlperf_task_java_proto_lite",
        "//third_party:androidx_annotation",
        "//third_party:androidx_appcompat",
        "//third_party:androidx_constraint_layout",
        "//third_party:androidx_core",
        "//third_party:androidx_material",
        "//third_party:androidx_preference",
        "//third_party:androidx_recyclerview",
    ],
)

android_binary(
    name = "mlperf_app",
    assets = glob([
        "assets/*.tflite",
        "assets/*.txt",
        "assets/*.pbtxt",
    ]),
    assets_dir = "assets",
    manifest = "AndroidManifest.xml",
    multidex = "native",
    tags = [
        "instrumentation",
        "no_cuda_on_cpu_tap",
    ],
    deps = [":evaluation_app_lib"],
)

filegroup(
    name = "all_files",
    srcs = glob(
        ["**/*"],
        exclude = [
            "**/METADATA",
            "**/OWNERS",
        ],
    ),
)

android_library(
    name = "jni_wrapper",
    srcs = [
        "MLPerfDriverWrapper.java",
        "NativeEvaluation.java",
    ],
    manifest = "AndroidManifest.xml",
    deps = [
        ":evaluation_native",
        ":mlperf_task_java_proto_lite",
    ],
)

cc_library(
    name = "evaluation_native",
    srcs = ["libmlperf_jni.so"],
)

tflite_jni_binary(
    name = "libmlperf_jni.so",
    deps = [
        "//java/org/mlperf/inference/jni:mlperf_jni",
    ],
)
