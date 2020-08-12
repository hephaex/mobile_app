# MLPerf backends

This directory provides a main binary file which can be used to evaluate the
mlperf benchmark with a specific set of (backend, dataset).

For example, the following command to run TFLite with the dummy dataset:

```bash
bazel run  -c opt --cxxopt='--std=c++14' --host_cxxopt='--std=c++14' -- \
  //cpp/binary:main TFLITE DUMMY \
  --mode=SubmissionRun \
  --model_file=<path to the model file> \
  --num_threads=4 \
  --delegate=None \
  --output_dir=<mlperf output directory>
```

Each set of (backend, dataset) has a different set of arguments, so please use
`--help` argument to check which flags are available. Ex:

```bash
bazel run  -c opt --cxxopt='--std=c++14' --host_cxxopt='--std=c++14' -- \
  //cpp/binary:main TFLITE IMAGENET --help
```

The supported backends and datasets for this binary is listed in the enum
BackendType and DatasetType in main.cc.
