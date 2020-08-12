/* Copyright 2019 The MLPerf Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/
#include <jni.h>

#include <memory>
#include <string>

#include "cpp/backend.h"
#include "cpp/dataset.h"
#include "cpp/mlperf_driver.h"
#include "tensorflow/lite/java/src/main/native/jni_utils.h"

using mlperf::mobile::Backend;
using mlperf::mobile::Dataset;
using mlperf::mobile::MlperfDriver;

MlperfDriver* convertLongToMlperfDriver(JNIEnv* env, jlong handle) {
  if (handle == 0) {
    tflite::jni::ThrowException(
        env, kIllegalArgumentException,
        "Internal error: Invalid handle to MlperfDriver.");
    return nullptr;
  }
  return reinterpret_cast<MlperfDriver*>(handle);
}

#ifdef __cplusplus
extern "C" {
#endif  // __cplusplus

JNIEXPORT jlong JNICALL
Java_org_mlperf_inference_MLPerfDriverWrapper_nativeInit(JNIEnv* env,
                                                         jclass clazz,
                                                         jlong dataset_handle,
                                                         jlong backend_handle) {
  if (dataset_handle == 0 || backend_handle == 0) {
    tflite::jni::ThrowException(env, kIllegalArgumentException,
                                "Internal error: Invalid handle.");
  }
  Dataset* dataset = reinterpret_cast<Dataset*>(dataset_handle);
  Backend* backend = reinterpret_cast<Backend*>(backend_handle);
  // create a new MlperfDriver
  std::unique_ptr<MlperfDriver> driver_ptr(new MlperfDriver(
      std::unique_ptr<Dataset>(dataset), std::unique_ptr<Backend>(backend)));
  return reinterpret_cast<jlong>(driver_ptr.release());
}

JNIEXPORT void JNICALL Java_org_mlperf_inference_MLPerfDriverWrapper_nativeRun(
    JNIEnv* env, jclass clazz, jlong driver_handle, jstring jmode,
    jstring jscenario, jint min_query_count, jint min_duration,
    jstring joutput_dir) {
  // Convert parameters to C++.
  std::string mode = env->GetStringUTFChars(jmode, nullptr);
  std::string scenario = env->GetStringUTFChars(jscenario, nullptr);
  std::string output_dir = env->GetStringUTFChars(joutput_dir, nullptr);
  // Start the test.
  convertLongToMlperfDriver(env, driver_handle)
      ->RunMLPerfTest(mode, scenario, min_query_count, min_duration,
                      output_dir);
}

JNIEXPORT jstring JNICALL
Java_org_mlperf_inference_MLPerfDriverWrapper_nativeGetLatency(
    JNIEnv* env, jclass clazz, jlong helper_handle) {
  std::string latency =
      convertLongToMlperfDriver(env, helper_handle)->ComputeLatencyString();
  return env->NewStringUTF(latency.c_str());
}

JNIEXPORT jstring JNICALL
Java_org_mlperf_inference_MLPerfDriverWrapper_nativeGetAccuracy(
    JNIEnv* env, jclass clazz, jlong helper_handle) {
  std::string accuracy =
      convertLongToMlperfDriver(env, helper_handle)->ComputeAccuracyString();
  return env->NewStringUTF(accuracy.c_str());
}

JNIEXPORT void JNICALL
Java_org_mlperf_inference_MLPerfDriverWrapper_nativeDelete(
    JNIEnv* env, jclass clazz, jlong driver_handle) {
  if (driver_handle != 0) {
    delete convertLongToMlperfDriver(env, driver_handle);
  }
}

#ifdef __cplusplus
}  // extern "C"
#endif  // __cplusplus
