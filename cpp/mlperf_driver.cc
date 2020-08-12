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
#include "cpp/mlperf_driver.h"

#include <stdint.h>

#include <memory>
#include <string>
#include <vector>

#include "cpp/backend.h"
#include "cpp/dataset.h"
#include "cpp/utils.h"
#include "loadgen/loadgen.h"
#include "loadgen/query_sample_library.h"
#include "loadgen/system_under_test.h"
#include "loadgen/test_settings.h"

namespace mlperf {
namespace mobile {

void MlperfDriver::IssueQuery(
    const std::vector<::mlperf::QuerySample>& samples) {
  std::vector<::mlperf::QuerySampleResponse> responses;
  std::vector<std::vector<uint8_t>> response_data;
  for (int idx = 0; idx < samples.size(); ++idx) {
    ::mlperf::QuerySample sample = samples.at(idx);
    std::vector<void*> inputs = dataset_->GetData(sample.index);
    backend_->SetInputs(inputs);
    backend_->IssueQuery();

    // Report to mlperf.
    std::vector<void*> outputs = backend_->GetPredictedOutputs();
    response_data.push_back(dataset_->ProcessOutput(sample.index, outputs));
    responses.push_back(
        {sample.id, reinterpret_cast<std::uintptr_t>(response_data[idx].data()),
         response_data[idx].size()});
  }
  ::mlperf::QuerySamplesComplete(responses.data(), responses.size());
}

void MlperfDriver::RunMLPerfTest(const std::string& mode,
                                 const std::string& scenario,
                                 int min_query_count, int min_duration,
                                 const std::string& output_dir) {
  // Setting the mlperf configs.
  ::mlperf::TestSettings mlperf_settings;
  ::mlperf::LogSettings log_settings;
  log_settings.log_output.outdir = output_dir;
  log_settings.log_output.copy_summary_to_stdout = true;

  if (scenario == kMobilenetOfflineScenario) {
    mlperf_settings.scenario = ::mlperf::TestScenario::Offline;
    mlperf_settings.performance_sample_count_override =
        kMobilenetOfflineSampleCount;
    mlperf_settings.min_duration_ms = kMobilenetOfflineMinDurationMs;
    mlperf_settings.offline_expected_qps = kMobilenetOfflineExpectedQps;
    mlperf_settings.mode = TestMode::PerformanceOnly;
    ::mlperf::StartTest(this, dataset_.get(), mlperf_settings, log_settings);
    return;
  } else {
    mlperf_settings.scenario = ::mlperf::TestScenario::SingleStream;
    mlperf_settings.single_stream_expected_latency_ns = 1000000;
    mlperf_settings.min_duration_ms = min_duration;
    mlperf_settings.min_query_count = min_query_count;
  }

  // Start the test.
  switch (Str2TestMode(mode)) {
    case TestMode::SubmissionRun:
      mlperf_settings.mode = TestMode::AccuracyOnly;
      ::mlperf::StartTest(this, dataset_.get(), mlperf_settings, log_settings);
      mlperf_settings.mode = TestMode::PerformanceOnly;
      ::mlperf::StartTest(this, dataset_.get(), mlperf_settings, log_settings);
      break;
    case TestMode::AccuracyOnly:
      mlperf_settings.mode = TestMode::AccuracyOnly;
      ::mlperf::StartTest(this, dataset_.get(), mlperf_settings, log_settings);
      break;
    case TestMode::PerformanceOnly:
      mlperf_settings.mode = TestMode::PerformanceOnly;
      ::mlperf::StartTest(this, dataset_.get(), mlperf_settings, log_settings);
      break;
    case TestMode::FindPeakPerformance:
      LOG(FATAL) << "FindPeakPerformance mode is not supported";
      break;
  }
}

}  // namespace mobile
}  // namespace mlperf
