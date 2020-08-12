#!/bin/bash

set -e
set -o pipefail
set -x
trap 'kill $$' ERR

./bazel-2.0.0-darwin-x86_64 --output_user_root=.github/workflows/cache/bazel build -c opt \
  --cxxopt=-std=c++14 --host_cxxopt=--std=c++14 --fat_apk_cpu=x86,arm64-v8a,armeabi-v7a \
  //androidTest:mlperf_test_app //java/org/mlperf/inference:mlperf_app

adb install -r bazel-bin/java/org/mlperf/inference/mlperf_app.apk
adb install -r bazel-bin/androidTest/mlperf_test_app.apk

output=$(adb shell am instrument -w org.mlperf.inference.test/androidx.test.runner.AndroidJUnitRunner)  || exit $?
if [[ $src =~ "Process crashed" ]]
then
  exit 1
fi

