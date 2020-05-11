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
import android.content.SharedPreferences;
import android.util.Log;
import androidx.preference.PreferenceManager;
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
      localDir = context.getFilesDir().getPath();
      SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
      String customConfig =
          sharedPref.getString(context.getString(R.string.custom_config_key), null);
      if (customConfig == null || !loadCustomConfig(customConfig)) {
        try {
          InputStream inputStream = context.getResources().openRawResource(R.raw.tasks_pb);
          mlperfTasks = MLPerfConfig.parseFrom(inputStream);
          inputStream.close();
        } catch (IOException e) {
          Log.e(TAG, "Unable to read config proto file");
        }
      }
    }
    return mlperfTasks;
  }

  public static boolean loadCustomConfig(String text) {
    try {
      mlperfTasks = MLPerfConfig.parseFrom(MLPerfDriverWrapper.convertProto(text));
    } catch (Exception e) {
      Log.e(TAG, "Failed to read text config file: " + e.getMessage());
      return false;
    }
    return true;
  }

  public static String getLocalPath(String path) {
    String filename = new File(path).getName();
    return localDir + "/cache/" + filename;
  }
}
