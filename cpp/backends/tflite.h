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
#ifndef MLPERF_BACKENDS_TFLITE_H_
#define MLPERF_BACKENDS_TFLITE_H_

#include "cpp/backend.h"
#include "cpp/utils.h"
#include "tensorflow/lite/tools/evaluation/stages/tflite_inference_stage.h"

namespace mlperf {
namespace mobile {

// TfliteBackend runs ML inferences with TFLite.
class TfliteBackend : public Backend {
 public:
  TfliteBackend(const std::string& model_file_path, int num_threads);

  // A human-readable string for logging purposes.
  const std::string& Name() const override { return name_; }

  // Apply the delegate, return error if failed.
  TfLiteStatus ApplyDelegate(const std::string& delegate);

  // Run inference for a sample.
  void IssueQuery() override {
    if (inference_stage_->Run() != kTfLiteOk) {
      LOG(FATAL) << "Error while inferencing model";
    }
  }

  // Flush the staged queries immediately.
  void FlushQueries() override{};

  // Sets inputs for a sample before inferencing.
  void SetInputs(const std::vector<void*>& inputs) override {
    inference_stage_->SetInputs(inputs);
  }

  // Returns the result after inferencing.
  std::vector<void*> GetPredictedOutputs() override;

  // Returns the input format required by the model.
  const DataFormat& GetInputFormat() override { return input_format_; }

  // Returns the output format produced by the model.
  const DataFormat& GetOutputFormat() override { return output_format_; }

 private:
  const std::string name_ = "TFLite";
  DataFormat input_format_;
  DataFormat output_format_;
  std::unique_ptr<tflite::evaluation::TfliteInferenceStage> inference_stage_;
};

}  // namespace mobile
}  // namespace mlperf
#endif  // MLPERF_BACKENDS_TFLITE_H_
