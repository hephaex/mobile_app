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
#ifndef MLPERF_UTILS_H_
#define MLPERF_UTILS_H_

#include <cstdint>
#include <numeric>
#include <string>
#include <vector>

#include "loadgen/test_settings.h"
#include "tensorflow/core/platform/logging.h"

namespace mlperf {
namespace mobile {

// Requirements of the data for a specific input. It contains the type and size
// of that input.
struct DataType {
  enum Type {
    Float32 = 0,
    Uint8 = 1,
    Int8 = 2,
    Float16 = 3,
    Int32 = 4,
    Int64 = 5,
  };

  DataType(Type t, int s) {
    type = t;
    size = s;
  }

  int GetByte() const {
    switch (type) {
      case Uint8:
        return 1;
      case Int8:
        return 1;
      case Float16:
        return 2;
      case Int32:
      case Float32:
        return 4;
      case Int64:
        return 8;
    }
  }

  Type type;
  int size;
};

// Requirements of the data including multiple inputs.
using DataFormat = std::vector<DataType>;

// Return topK indexes with highest probability.
template <typename T>
inline std::vector<int32_t> GetTopK(T* values, int num_elem, int k,
                                    int offset) {
  std::vector<int32_t> indices(num_elem - offset);
  std::iota(indices.begin(), indices.end(), 0);
  std::sort(indices.begin(), indices.end(), [&values, offset](int a, int b) {
    return values[a + offset] > values[b + offset];
  });
  indices.resize(k);
  return indices;
}

// Convert string to mlperf::TestMode.
inline ::mlperf::TestMode Str2TestMode(const std::string& mode) {
  if (mode == "PerformanceOnly") {
    return ::mlperf::TestMode::PerformanceOnly;
  } else if (mode == "AccuracyOnly") {
    return ::mlperf::TestMode::AccuracyOnly;
  } else if (mode == "SubmissionRun") {
    return ::mlperf::TestMode::SubmissionRun;
  } else {
    LOG(ERROR) << "Mode " << mode << " is not supported";
    return ::mlperf::TestMode::PerformanceOnly;
  }
}

const std::string kMobilenetOfflineScenario = "mobilenet-offline";

const size_t kMobilenetOfflineSampleCount = 5000;
// TODO: Move the following parameters to mlperf_task.proto
// These parameters will set 1100 samples_per_query. (10% more queries included)
// For 11000 samples_per_query, use 10000, 1000
const size_t kMobilenetOfflineMinDurationMs = 5000;
const size_t kMobilenetOfflineExpectedQps = 200;

}  // namespace mobile
}  // namespace mlperf

#endif  // MLPERF_UTILS_H_
