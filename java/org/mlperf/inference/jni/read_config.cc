/* Copyright 2020 The MLPerf Authors. All Rights Reserved.

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

#include "cpp/proto/mlperf_task.pb.h"
#include "google/protobuf/text_format.h"

#ifdef __cplusplus
extern "C" {
#endif  // __cplusplus

JNIEXPORT jbyteArray JNICALL
Java_org_mlperf_inference_MLPerfDriverWrapper_convertProto(JNIEnv* env,
                                                           jclass clazz,
                                                           jstring jtext) {
  // Convert parameters to C++.
  std::string text = env->GetStringUTFChars(jtext, nullptr);
  std::string binary;

  // Convert text proto to binary.
  mlperf::mobile::MLPerfConfig config;
  if (!google::protobuf::TextFormat::ParseFromString(text, &config)) {
    env->ThrowNew(env->FindClass("java/lang/Exception"),
                  "Failed to parse the proto file. Please check its format.");
  }
  if (!config.SerializeToString(&binary)) {
    env->ThrowNew(env->FindClass("java/lang/Exception"),
                  "Failed to write proto to string.");
  }
  jbyteArray result = env->NewByteArray(binary.size());
  env->SetByteArrayRegion(result, 0, binary.size(), (jbyte*)binary.c_str());
  return result;
}

#ifdef __cplusplus
}  // extern "C"
#endif  // __cplusplus
