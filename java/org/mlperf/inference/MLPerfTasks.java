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
package org.mlperf.inference;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.mlperf.proto.MLPerfConfig;

/** This class reads the tasks.pbtxt and provides quick inference to its values. */
final class MLPerfTasks {
  private static final String TAG = "MLPerfTasks";
  private static MLPerfConfig mlperfTasks;
  private static String localDir;

  // Make this class not instantiable.
  private MLPerfTasks() {}

  public static MLPerfConfig getConfig(Context context) {
    if (mlperfTasks == null) {
      // The proto file is checked at compile time, the exception is unlikely to happen.
      try {
        InputStream inputStream = context.getResources().openRawResource(R.raw.tasks_pb);
        mlperfTasks = MLPerfConfig.parseFrom(inputStream);
        inputStream.close();
        localDir = context.getFilesDir().getPath();
      } catch (IOException e) {
        Log.e(TAG, "Unable to read config proto file");
      }
    }
    return mlperfTasks;
  }

  public static String getLocalPath(String path) {
    String filename = new File(path).getName();
    return localDir + "/cache/" + filename;
  }
}
