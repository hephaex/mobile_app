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
#include "cpp/datasets/dummy_dataset.h"

#include <algorithm>
#include <cstdint>
#include <cstdlib>
#include <vector>

#include "cpp/utils.h"

namespace mlperf {
namespace mobile {

void DummyDataset::LoadSamplesToRam(
    const std::vector<QuerySampleIndex>& samples) {
  for (QuerySampleIndex sample_idx : samples) {
    switch (dataset_type_) {
      case DatasetConfig::MOBILEBERT: {
        // Input_ids is in range [0, 30000).
        std::vector<uint8_t>* input_ids =
            new std::vector<uint8_t>(input_format_[0].size * 4);
        std::generate_n(reinterpret_cast<int32_t*>(input_ids->data()),
                        input_format_[0].size, [] { return random() % 30000; });
        samples_.at(sample_idx).push_back(input_ids);
        // Input_mask is in range [0,1].
        std::vector<uint8_t>* input_mask =
            new std::vector<uint8_t>(input_format_[0].size * 4);
        std::generate_n(reinterpret_cast<int32_t*>(input_mask->data()),
                        input_format_[0].size, [] { return random() % 2; });
        samples_.at(sample_idx).push_back(input_mask);
        // Segment_ids is in range [0,1].
        std::vector<uint8_t>* segment_ids =
            new std::vector<uint8_t>(input_format_[0].size * 4);
        std::generate_n(reinterpret_cast<int32_t*>(segment_ids->data()),
                        input_format_[0].size, [] { return random() % 2; });
        samples_.at(sample_idx).push_back(segment_ids);
      } break;
      default: {
        for (const DataType& data_type : input_format_) {
          int type_size = data_type.GetByte();
          std::vector<uint8_t>* data =
              new std::vector<uint8_t>(data_type.size * type_size);
          for (auto it = data->begin(); it != data->end(); ++it) {
            *it = random();
          }
          samples_.at(sample_idx).push_back(data);
        }
      } break;
    }
  }
}

void DummyDataset::UnloadSamplesFromRam(
    const std::vector<QuerySampleIndex>& samples) {
  for (QuerySampleIndex sample_idx : samples) {
    for (std::vector<uint8_t>* v : samples_.at(sample_idx)) {
      delete v;
    }
    samples_.at(sample_idx).clear();
  }
}

}  // namespace mobile
}  // namespace mlperf
