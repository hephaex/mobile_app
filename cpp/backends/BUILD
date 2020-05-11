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
    name = "tflite",
    srcs = ["tflite.cc"],
    hdrs = [
        "tflite.h",
    ],
    copts = tflite_copts(),
    deps = [
        "//cpp:mlperf_driver",
        "//cpp:utils",
        "@com_google_absl//absl/strings",
        "@org_tensorflow//tensorflow/lite/kernels:kernel_util",
        "@org_tensorflow//tensorflow/lite/tools/evaluation/proto:evaluation_stages_cc_proto",
        "@org_tensorflow//tensorflow/lite/tools/evaluation/stages:tflite_inference_stage",
    ],
)

cc_library(
    name = "dummy_backend",
    srcs = ["dummy_backend.cc"],
    hdrs = [
        "dummy_backend.h",
    ],
    copts = tflite_copts(),
    deps = [
        "//cpp:mlperf_driver",
        "//cpp:utils",
        "@backend_dummy_api//dummy_api",
    ],
)
