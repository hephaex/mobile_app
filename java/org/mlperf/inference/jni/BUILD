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

load("@org_tensorflow//tensorflow/lite:build_def.bzl", "tflite_copts")

package(
    default_visibility = ["//visibility:public"],
    licenses = ["notice"],  # Apache 2.0
)

cc_library(
    name = "mlperf_jni",
    srcs = [
        "backend_jni.cc",
        "dataset_jni.cc",
        "mlperf_driver_jni.cc",
    ],
    copts = tflite_copts(),
    linkopts = [
        "-lm",
        "-ldl",
    ],
    deps = [
        "//cpp:mlperf_driver",
        "//cpp/backends:tflite",
        "//cpp/datasets:coco",
        "//cpp/datasets:dummy_dataset",
        "//cpp/datasets:imagenet",
        "@org_tensorflow//tensorflow/lite/java/src/main/native",
        "@org_tensorflow//tensorflow/lite/java/src/main/native:native_framework_only",
        "@org_tensorflow//tensorflow/lite/nnapi:nnapi_implementation",
    ],
    alwayslink = 1,
)
