#!/bin/bash

set -e
set -o pipefail
set -x
trap 'kill $$' ERR

bazel --output_user_root=.github/workflows/cache build -c opt \
  --cxxopt=-std=c++17 --fat_apk_cpu=x86,arm64-v8a,armeabi-v7a \
  //androidTest:mlperf_test_app //java/org/mlperf/inference:mlperf_app

adb install -r bazel-bin/java/org/mlperf/inference/mlperf_app.apk
adb install -r bazel-bin/androidTest/mlperf_test_app.apk

adb shell am instrument -w org.mlperf.inference.test/androidx.test.runner.AndroidJUnitRunner

