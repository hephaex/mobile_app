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

#include "cpp/backends/tflite.h"
#include "tensorflow/lite/nnapi/nnapi_implementation.h"

#ifdef __cplusplus
extern "C" {
#endif  // __cplusplus

JNIEXPORT jlong JNICALL Java_org_mlperf_inference_MLPerfDriverWrapper_tflite(
    JNIEnv* env, jclass clazz, jstring jmodel_file_path, jint num_threads,
    jstring jdelegate) {
  // Convert parameters to C++.
  std::string model_file_path =
      env->GetStringUTFChars(jmodel_file_path, nullptr);
  std::string delegate = env->GetStringUTFChars(jdelegate, nullptr);

  // Create a new TfliteBackend object.
  std::unique_ptr<mlperf::mobile::TfliteBackend> backend_ptr(
      new mlperf::mobile::TfliteBackend(model_file_path, num_threads));
  if (backend_ptr->ApplyDelegate(delegate) != 0) {
    env->ThrowNew(env->FindClass("java/lang/Exception"),
                  "failed to apply delegate");
  }
  return reinterpret_cast<jlong>(backend_ptr.release());
}

JNIEXPORT void JNICALL
Java_org_mlperf_inference_MLPerfDriverWrapper_nativeDeleteBackend(
    JNIEnv* env, jclass clazz, jlong handle) {
  if (handle != 0) {
    delete reinterpret_cast<mlperf::mobile::Backend*>(handle);
  }
}

JNIEXPORT jobject JNICALL
Java_org_mlperf_inference_MLPerfDriverWrapper_listDevicesForNNAPI(
    JNIEnv* env, jclass clazz) {
  jclass java_util_ArrayList = env->FindClass("java/util/ArrayList");
  jmethodID java_util_ArrayList_init =
      env->GetMethodID(java_util_ArrayList, "<init>", "(I)V");
  jmethodID java_util_ArrayList_add =
      env->GetMethodID(java_util_ArrayList, "add", "(Ljava/lang/Object;)Z");
  const NnApi* nnapi = NnApiImplementation();

  if (nnapi->ANeuralNetworks_getDeviceCount != nullptr) {
    uint32_t num_devices = 0;
    NnApiImplementation()->ANeuralNetworks_getDeviceCount(&num_devices);

    jobject results = env->NewObject(java_util_ArrayList,
                                     java_util_ArrayList_init, num_devices);

    for (uint32_t i = 0; i < num_devices; i++) {
      ANeuralNetworksDevice* device = nullptr;
      const char* buffer = nullptr;
      nnapi->ANeuralNetworks_getDevice(i, &device);
      nnapi->ANeuralNetworksDevice_getName(device, &buffer);
      jstring device_name = env->NewStringUTF(buffer);
      env->CallBooleanMethod(results, java_util_ArrayList_add, device_name);
      env->DeleteLocalRef(device_name);
    }
    return results;
  }
  return env->NewObject(java_util_ArrayList, java_util_ArrayList_init, 0);
}

#ifdef __cplusplus
}  // extern "C"
#endif  // __cplusplus
