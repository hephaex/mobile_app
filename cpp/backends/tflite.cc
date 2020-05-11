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
#include "cpp/backends/tflite.h"

#include <memory>
#include <string>
#include <vector>

#include "absl/strings/match.h"
#include "cpp/backend.h"
#include "cpp/utils.h"
#include "tensorflow/lite/kernels/kernel_util.h"
#include "tensorflow/lite/tools/evaluation/proto/evaluation_stages.pb.h"
#include "tensorflow/lite/tools/evaluation/stages/tflite_inference_stage.h"
#include "tensorflow/lite/tools/evaluation/utils.h"

namespace mlperf {
namespace mobile {
namespace {
// Convert TfLiteType to DataType.
inline DataType::Type TfType2DataType(TfLiteType type) {
  switch (type) {
    case kTfLiteFloat32:
      return DataType::Float32;
    case kTfLiteUInt8:
      return DataType::Uint8;
    case kTfLiteInt8:
      return DataType::Int8;
    case kTfLiteFloat16:
      return DataType::Float16;
    case kTfLiteInt32:
      return DataType::Int32;
    case kTfLiteInt64:
      return DataType::Int64;
    default:
      LOG(FATAL) << "TfLiteType " << type << " not supported";
      return DataType::Float32;
  }
}

}  // namespace

TfliteBackend::TfliteBackend(const std::string& model_file_path,
                             int num_threads) {
  tflite::evaluation::EvaluationStageConfig inference_config;
  inference_config.set_name("inference_stage");
  auto* inference_params = inference_config.mutable_specification()
                               ->mutable_tflite_inference_params();
  inference_params->set_invocations_per_run(1);
  inference_params->set_model_file_path(model_file_path);
  inference_params->set_num_threads(num_threads);

  inference_stage_.reset(
      new tflite::evaluation::TfliteInferenceStage(inference_config));
  if (inference_stage_->Init() != kTfLiteOk) {
    LOG(FATAL) << "Init inference stage failed";
  }

  // Collect input and output formats.
  const tflite::evaluation::TfLiteModelInfo* model_info =
      inference_stage_->GetModelInfo();
  for (const TfLiteTensor* input : model_info->inputs) {
    input_format_.emplace_back(TfType2DataType(input->type),
                               tflite::NumElements(input));
  }
  for (const TfLiteTensor* output : model_info->outputs) {
    output_format_.emplace_back(TfType2DataType(output->type),
                                tflite::NumElements(output));
  }
}

TfLiteStatus TfliteBackend::ApplyDelegate(const std::string& delegate) {
  LOG(INFO) << "Applying delegate: " << delegate;
  tflite::Interpreter::TfLiteDelegatePtr delegate_ptr(nullptr,
                                                      [](TfLiteDelegate*) {});
#if defined(__ANDROID__)
  if (absl::StartsWithIgnoreCase(delegate, "GPU")) {
    TfLiteGpuDelegateOptionsV2 gpu_opts = TfLiteGpuDelegateOptionsV2Default();
    gpu_opts.inference_preference =
        TFLITE_GPU_INFERENCE_PREFERENCE_SUSTAINED_SPEED;
    if (absl::EqualsIgnoreCase(delegate, "GPU (F16)")) {
      gpu_opts.inference_priority1 = TFLITE_GPU_INFERENCE_PRIORITY_MIN_LATENCY;
    } else {
      gpu_opts.inference_priority1 =
          TFLITE_GPU_INFERENCE_PRIORITY_MAX_PRECISION;
    }
    delegate_ptr = tflite::evaluation::CreateGPUDelegate(&gpu_opts);
  } else if (absl::StartsWithIgnoreCase(delegate, "nnapi")) {
    tflite::StatefulNnApiDelegate::Options options;
    options.execution_preference =
        tflite::StatefulNnApiDelegate::Options::kSustainedSpeed;
    std::string accelerator_name = absl::StrContains(delegate, "-")
                                       ? delegate.substr(delegate.find('-') + 1)
                                       : std::string();
    if (!accelerator_name.empty()) {
      options.accelerator_name = accelerator_name.c_str();
    }
    delegate_ptr = tflite::evaluation::CreateNNAPIDelegate(options);
  }
#endif

  if (inference_stage_->ApplyCustomDelegate(std::move(delegate_ptr)) !=
      kTfLiteOk) {
    LOG(ERROR) << "Applying delegate failed";
    return kTfLiteError;
  }
  return kTfLiteOk;
}

std::vector<void*> TfliteBackend::GetPredictedOutputs() {
  std::vector<void*> outputs;
  for (const TfLiteTensor* output_tensor :
       inference_stage_->GetModelInfo()->outputs) {
    switch (output_tensor->type) {
      case kTfLiteFloat32:
        outputs.push_back(output_tensor->data.f);
        break;
      case kTfLiteUInt8:
        outputs.push_back(output_tensor->data.uint8);
        break;
      case kTfLiteInt8:
        outputs.push_back(output_tensor->data.int8);
        break;
      case kTfLiteFloat16:
        outputs.push_back(output_tensor->data.f16);
        break;
      case kTfLiteInt64:
        outputs.push_back(output_tensor->data.i64);
        break;
      default:
        LOG(FATAL) << "Data type not yet supported";
        break;
    }
  }
  return outputs;
}

}  // namespace mobile
}  // namespace mlperf
