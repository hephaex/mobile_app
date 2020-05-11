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
#include "cpp/backends/dummy_backend.h"

#include <memory>
#include <string>
#include <vector>

#include "dummy_api/dummy_api.h"

namespace mlperf {
namespace mobile {

DummyBackend::DummyBackend(const std::string& model_file_path) {
  dummyapi::InitializeBackend(model_file_path);
  for (const dummyapi::DataInfo& info : dummyapi::GetInputFormat()) {
    input_format_.emplace_back(
        static_cast<DataType::Type>(static_cast<int>(info.type)), info.length);
  }
  for (const dummyapi::DataInfo& info : dummyapi::GetOutputFormat()) {
    output_format_.emplace_back(
        static_cast<DataType::Type>(static_cast<int>(info.type)), info.length);
  }
}

void DummyBackend::IssueQuery() { dummyapi::Run(); }

void DummyBackend::SetInputs(const std::vector<void*>& inputs) {
  dummyapi::SetInputs(inputs);
}

std::vector<void*> DummyBackend::GetPredictedOutputs() {
  std::vector<void*> outputs;
  std::vector<std::vector<uint8_t>> predicted_outputs = dummyapi::GetOutputs();
  for (int i = 0; i < predicted_outputs.size(); ++i) {
    outputs.push_back(predicted_outputs[i].data());
  }
  return outputs;
}

}  // namespace mobile
}  // namespace mlperf
