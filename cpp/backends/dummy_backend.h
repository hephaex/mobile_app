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
#ifndef MLPERF_BACKENDS_DUMMY_BACKEND_H_
#define MLPERF_BACKENDS_DUMMY_BACKEND_H_

#include "cpp/backend.h"
#include "cpp/utils.h"

namespace mlperf {
namespace mobile {

// DummyBackend takes a model and returns random output.
class DummyBackend : public Backend {
 public:
  DummyBackend(const std::string& model_file_path);

  // A human-readable string for logging purposes.
  const std::string& Name() const override { return name_; }

  // Call the Vendor API to run inference for a sample.
  void IssueQuery() override;

  // Flush the staged queries immediately.
  void FlushQueries() override {}

  // Call the Vendor API to pass the inputs.
  void SetInputs(const std::vector<void*>& inputs) override;

  // Call the Vendor API to get the outputs.
  std::vector<void*> GetPredictedOutputs() override;

  // Returns the input format required by the model.
  const DataFormat& GetInputFormat() override { return input_format_; }

  // Returns the output format produced by the model.
  const DataFormat& GetOutputFormat() override { return output_format_; }

 private:
  const std::string name_ = "DummyBackend";
  DataFormat input_format_;
  DataFormat output_format_;
};

}  // namespace mobile
}  // namespace mlperf
#endif  // MLPERF_BACKENDS_DUMMY_BACKEND_H_
