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
#include <fstream>
#include <iostream>
#include <string>
#include <utility>
#include <vector>

#include "absl/strings/match.h"
#include "cpp/backends/tflite.h"
#include "cpp/datasets/coco.h"
#include "cpp/datasets/dummy_dataset.h"
#include "cpp/datasets/imagenet.h"
#include "cpp/mlperf_driver.h"
#include "cpp/proto/mlperf_task.pb.h"
#include "cpp/utils.h"
#include "tensorflow/lite/tools/command_line_flags.h"

namespace mlperf {
namespace mobile {
namespace {
// Supported backends.
enum class BackendType {
  NONE = 0,
  TFLITE = 1,
};

BackendType Str2BackendType(absl::string_view name) {
  if (absl::EqualsIgnoreCase(name, "TFLITE")) {
    return BackendType::TFLITE;
  } else {
    return BackendType::NONE;
  }
}

DatasetConfig::DatasetType Str2DatasetType(absl::string_view name) {
  if (absl::EqualsIgnoreCase(name, "COCO")) {
    return DatasetConfig::COCO;
  } else if (absl::EqualsIgnoreCase(name, "IMAGENET")) {
    return DatasetConfig::IMAGENET;
  } else if (absl::EqualsIgnoreCase(name, "MOBILEBERT")) {
    return DatasetConfig::MOBILEBERT;
  } else if (absl::EqualsIgnoreCase(name, "DUMMY")) {
    return DatasetConfig::NONE;
  } else {
    LOG(FATAL) << "Unregconized dataset type: " << name;
    return DatasetConfig::NONE;
  }
}

}  // namespace

int Main(int argc, char* argv[]) {
  using tflite::Flag;
  using tflite::Flags;
  std::string command_line = argv[0];
  // Flags for backend and dataset.
  std::string backend_name, dataset_name;
  BackendType backend_type = BackendType::NONE;
  DatasetConfig::DatasetType dataset_type = DatasetConfig::NONE;
  std::vector<Flag> flag_list{
      Flag::CreateFlag("backend", &backend_name,
                       "Backend. Only TFLite is supported at the moment.",
                       Flag::POSITIONAL),
      Flag::CreateFlag("dataset", &dataset_name,
                       "Dataset. One of imagenet, coco, mobilebert or dummy.",
                       Flag::POSITIONAL)};
  Flags::Parse(&argc, const_cast<const char**>(argv), flag_list);
  backend_type = Str2BackendType(backend_name);
  dataset_type = Str2DatasetType(dataset_name);
  if (backend_type == BackendType::NONE) {
    LOG(FATAL) << Flags::Usage(command_line, flag_list);
    return 1;
  }

  // Treats positional flags as subcommands.
  command_line += " " + backend_name + " " + dataset_name;

  // Command Line Flags for mlperf.
  std::string mode, output_dir;
  int min_query_count = 100, min_duration = 100;
  flag_list.clear();
  flag_list.insert(
      flag_list.end(),
      {Flag::CreateFlag("mode", &mode,
                        "Mode is one among PerformanceOnly, "
                        "AccuracyOnly, SubmissionRun.",
                        Flag::REQUIRED),
       Flag::CreateFlag("min_query_count", &min_query_count,
                        "The test will guarantee to run at least this "
                        "number of samples in performance mode."),
       Flag::CreateFlag("min_duration", &min_duration,
                        "The test will guarantee to run at least this "
                        "duration in performance mode. The duration is in ms."),
       Flag::CreateFlag("output_dir", &output_dir,
                        "The output directory of mlperf.", Flag::REQUIRED)});

  // Command Line Flags for backend.
  std::unique_ptr<Backend> backend;
  switch (backend_type) {
    case BackendType::TFLITE: {
      LOG(INFO) << "Using TFLite backend";
      std::string model_file_path;
      int num_threads = 1;
      std::string delegate = "none";
      flag_list.insert(
          flag_list.end(),
          {Flag::CreateFlag("model_file", &model_file_path,
                            "Path to TFLite model file.", Flag::REQUIRED),
           Flag::CreateFlag("num_threads", &num_threads,
                            "Number of interpreter threads for inference."),
           Flag::CreateFlag("delegate", &delegate,
                            "Delegate for inference, if available. "
                            "Can be one value of {'nnapi', 'nnapi-{accelerator "
                            "name}', 'gpu', 'gpu (f16)', 'none'}.")});
      if (Flags::Parse(&argc, const_cast<const char**>(argv), flag_list)) {
        TfliteBackend* tflite_backend =
            new TfliteBackend(model_file_path, num_threads);
        if (tflite_backend->ApplyDelegate(delegate) != kTfLiteOk) {
          LOG(INFO) << "Cannot apply the delegate.";
          delete tflite_backend;
          return 1;
        }
        backend.reset(tflite_backend);
      }
    } break;
    default:
      break;
  }

  // Command Line Flags for dataset.
  std::unique_ptr<Dataset> dataset;
  switch (dataset_type) {
    case DatasetConfig::IMAGENET: {
      LOG(INFO) << "Using Imagenet dataset";
      std::string images_directory, groundtruth_file;
      int offset = 1, image_width = 224, image_height = 224;
      std::vector<Flag> dataset_flags{
          Flag::CreateFlag("images_directory", &images_directory,
                           "Path to ground truth images.", Flag::REQUIRED),
          Flag::CreateFlag("offset", &offset,
                           "The offset of the first meaningful class in the "
                           "classification model.",
                           Flag::REQUIRED),
          Flag::CreateFlag("groundtruth_file", &groundtruth_file,
                           "Path to the imagenet ground truth file.",
                           Flag::REQUIRED),
          Flag::CreateFlag("image_width", &image_width,
                           "The width of the processed image."),
          Flag::CreateFlag("image_height", &image_height,
                           "The height of the processed image.")};
      if (Flags::Parse(&argc, const_cast<const char**>(argv), dataset_flags) &&
          backend) {
        dataset.reset(new Imagenet(backend->GetInputFormat(),
                                   backend->GetOutputFormat(), images_directory,
                                   groundtruth_file, offset, image_width,
                                   image_height));
      }
      // Adds to flag_list for showing help.
      flag_list.insert(flag_list.end(), dataset_flags.begin(),
                       dataset_flags.end());
    } break;
    case DatasetConfig::COCO: {
      LOG(INFO) << "Using Coco dataset";
      std::string images_directory, groundtruth_file;
      int offset = 1, num_classes = 91, image_width = 300, image_height = 300;
      std::vector<Flag> dataset_flags{
          Flag::CreateFlag("images_directory", &images_directory,
                           "Path to ground truth images.", Flag::REQUIRED),
          Flag::CreateFlag("offset", &offset,
                           "The offset of the first meaningful class in the "
                           "classification model.",
                           Flag::REQUIRED),
          Flag::CreateFlag("num_classes", &num_classes,
                           "The number of classes in the model outputs.",
                           Flag::REQUIRED),
          Flag::CreateFlag("groundtruth_file", &groundtruth_file,
                           "Path to the imagenet ground truth file.",
                           Flag::REQUIRED),
          Flag::CreateFlag("image_width", &image_width,
                           "The width of the processed image."),
          Flag::CreateFlag("image_height", &image_height,
                           "The height of the processed image.")};
      if (Flags::Parse(&argc, const_cast<const char**>(argv), dataset_flags) &&
          backend) {
        dataset.reset(new Coco(backend->GetInputFormat(),
                               backend->GetOutputFormat(), images_directory,
                               groundtruth_file, offset, num_classes,
                               image_width, image_height));
      }
      // Adds to flag_list for showing help.
      flag_list.insert(flag_list.end(), dataset_flags.begin(),
                       dataset_flags.end());
    } break;
    case DatasetConfig::MOBILEBERT: {
      LOG(INFO) << "MobileBert is currently supported with random input.";
      if (backend) {
        dataset.reset(new DummyDataset(backend->GetInputFormat(),
                                       backend->GetOutputFormat(),
                                       DatasetConfig::MOBILEBERT));
      }
    } break;
    case DatasetConfig::NONE: {
      LOG(INFO) << "Using Dummy dataset";
      if (backend) {
        dataset.reset(new DummyDataset(backend->GetInputFormat(),
                                       backend->GetOutputFormat(),
                                       dataset_type));
      }
    } break;
    default:
      break;
  }

  // Show usage if needed.
  if (!backend || !dataset) {
    LOG(FATAL) << Flags::Usage(command_line, flag_list);
    return 1;
  }
  // If using the dummy dataset, only run the performance mode.
  if (dataset_type == DatasetConfig::NONE) {
    mode = "PerformanceOnly";
  }

  // Running mlperf.
  MlperfDriver driver(std::move(dataset), std::move(backend));
  driver.RunMLPerfTest(mode, min_query_count, min_duration, output_dir);
  LOG(INFO) << "90 percentile latency: " << driver.ComputeLatencyString();
  LOG(INFO) << "Accuracy: " << driver.ComputeAccuracyString();
  return 0;
}

}  // namespace mobile
}  // namespace mlperf

int main(int argc, char* argv[]) { return mlperf::mobile::Main(argc, argv); }
