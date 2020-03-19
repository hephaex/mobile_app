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

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Messenger;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.mlperf.proto.DatasetConfig;
import org.mlperf.proto.MLPerfConfig;
import org.mlperf.proto.ModelConfig;
import org.mlperf.proto.TaskConfig;

/** {@link MLPerfEvaluation} evaluates models on MLPerf benchmark. */
public class MLPerfEvaluation extends AppCompatActivity implements Handler.Callback {

  private static final String TAG = "MLPerfEvaluation";
  private static final String ASSETS_PREFIX = "@assets/";

  private ProgressCount progressCount;
  private TextView taskResultText;
  private View dividerBar;
  private RecyclerView resultRecyclerView;
  private ResultsAdapter resultAdapter;
  private final ArrayList<ResultHolder> results = new ArrayList<>();
  private final HashMap<String, Integer> resultMap = new HashMap<>();

  private Set<String> delegates;
  private int numThreadsPreference;
  private int highLightColor;
  private int backgroundColor;

  private MLPerfConfig mlperfTasks;
  private HandlerThread workerThread;
  private RunMLPerfWorker workerHandler;
  private Messenger replyMessenger;

  private boolean modelIsAvailable = false;
  private SharedPreferences sharedPref;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    // Sets up the RecyclerView which shows results.
    resultRecyclerView = findViewById(R.id.results_recycler_view);
    resultRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    resultRecyclerView.setItemAnimator(new ResultItemAnimator());
    resultAdapter = new ResultsAdapter(this, results);
    resultRecyclerView.setAdapter(resultAdapter);
    highLightColor = ContextCompat.getColor(this, R.color.mlperfBlue);
    backgroundColor = ContextCompat.getColor(this, R.color.background);

    // Sets up progress bar and log area.
    ProgressBar progressBar = findViewById(R.id.progressBar);
    taskResultText = findViewById(R.id.taskResultText);
    taskResultText.setMovementMethod(new ScrollingMovementMethod());
    dividerBar = findViewById(R.id.divider);

    // Sets up menu buttons.
    ImageView playButton = findViewById(R.id.action_play);
    playButton.setOnClickListener(this::playButtonListener);
    ImageView stopButton = findViewById(R.id.action_stop);
    stopButton.setOnClickListener(this::stopButtonListener);
    ImageView refreshButton = findViewById(R.id.action_refresh);
    refreshButton.setOnClickListener(this::refreshButtonListener);
    ImageView settingButton = findViewById(R.id.action_settings);
    settingButton.setOnClickListener(this::settingButtonListener);

    // Reads tasks from proto file.
    mlperfTasks = MLPerfTasks.getConfig(getApplicationContext());

    // Runs all models by default after installing.
    sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
    if (sharedPref.getStringSet(getString(R.string.models_preference_key), null) == null) {
      SharedPreferences.Editor preferencesEditor = sharedPref.edit();
      Set<String> allModels = new HashSet<>();
      for (TaskConfig task : mlperfTasks.getTaskList()) {
        for (ModelConfig model : task.getModelList()) {
          allModels.add(model.getName());
        }
      }
      preferencesEditor.putStringSet(getString(R.string.models_preference_key), allModels);
      preferencesEditor.commit();
    }

    // Handles the result from RunMLPerfWorker.
    replyMessenger = new Messenger(new Handler(this.getMainLooper(), this));

    // Checks if models are available.
    checkModelIsAvailable();
    progressCount = new ProgressCount(progressBar, getWindow());
  }

  @Override
  public void onResume() {
    super.onResume();
    // Updates the shared preference.
    delegates = sharedPref.getStringSet(getString(R.string.pref_delegate_key), null);
    numThreadsPreference =
        Integer.parseInt(
            sharedPref.getString(
                getString(R.string.num_threads_key), getString(R.string.num_threads_default)));
    String logInfoPreference =
        sharedPref.getString(getString(R.string.pref_loginfo_key), getString(R.string.log_short));
    if (logInfoPreference.equals(getString(R.string.log_short))) {
      dividerBar.setVisibility(View.VISIBLE);
      taskResultText.setVisibility(View.VISIBLE);
      taskResultText.setMaxLines(4);
    } else if (logInfoPreference.equals(getString(R.string.log_full))) {
      dividerBar.setVisibility(View.VISIBLE);
      taskResultText.setVisibility(View.VISIBLE);
      taskResultText.setMaxLines(12);
    } else if (logInfoPreference.equals(getString(R.string.log_none))) {
      dividerBar.setVisibility(View.INVISIBLE);
      taskResultText.setVisibility(View.GONE);
    } else {
      dividerBar.setVisibility(View.INVISIBLE);
      taskResultText.setVisibility(View.GONE);
      Log.e(TAG, "Unknown LogInfo perference value: " + logInfoPreference);
    }
  }

  @Override
  public boolean handleMessage(Message inputMessage) {
    switch (inputMessage.what) {
      case RunMLPerfWorker.REPLY_UPDATE:
        String update = (String) inputMessage.obj;
        logProgress(update);
        break;
      case RunMLPerfWorker.REPLY_COMPLETE:
        ResultHolder result = (ResultHolder) inputMessage.obj;
        addNewResult(result);
        progressCount.increaseProgress();
        break;
      case RunMLPerfWorker.REPLY_ERROR:
        String error = (String) inputMessage.obj;
        // Set the color of error messages to red.
        SpannableString sb = new SpannableString(error);
        sb.setSpan(
            new ForegroundColorSpan(Color.RED),
            0,
            error.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        taskResultText.append(System.getProperty("line.separator"));
        taskResultText.append(sb);
        progressCount.increaseProgress();
        break;
      case RunMLPerfWorker.REPLY_CANCEL:
        String message = (String) inputMessage.obj;
        logProgress(message);
        progressCount.decreaseTotal();
        break;
      default:
        return false;
    }
    return true;
  }

  private void logProgress(String msg) {
    taskResultText.append(System.getProperty("line.separator"));
    taskResultText.append(msg);
    Log.i(TAG, "logProgress: " + msg);
  }

  private void playButtonListener(View v) {
    Set<String> selectedModels =
        sharedPref.getStringSet(getString(R.string.models_preference_key), null);
    if (selectedModels.isEmpty()) {
      logProgress("No models selected. Please select models in settings.");
      return;
    }
    if (checkModelIsAvailable()) {
      if (workerThread == null) {
        workerThread = new HandlerThread("MLPerf.Worker");
        workerThread.start();
        workerHandler = new RunMLPerfWorker(this, workerThread.getLooper());
      }
      for (int taskIdx = 0; taskIdx < mlperfTasks.getTaskCount(); ++taskIdx) {
        TaskConfig task = mlperfTasks.getTask(taskIdx);
        for (int modelIdx = 0; modelIdx < task.getModelCount(); ++modelIdx) {
          if (selectedModels.contains(task.getModel(modelIdx).getName())) {
            for (String delegate : delegates) {
              scheduleInference(taskIdx, modelIdx, delegate);
            }
          }
        }
      }
    } else {
      logProgress("Models are not available.");
    }
  }

  private void stopButtonListener(View v) {
    // Loadgen does not provide any method to stop the current task so the it will get finished.
    if (workerThread != null) {
      workerHandler.removeMessages();
      workerThread.quit();
      workerThread = null;
    }
  }

  private void refreshButtonListener(View v) {
    results.clear();
    resultMap.clear();
    resultAdapter.notifyDataSetChanged();
    // Fix: if an item is updated, the cache of older item is visable after deleting newer one.
    resultRecyclerView.setLayoutManager(new LinearLayoutManager(this));
  }

  private void settingButtonListener(View v) {
    Intent intent = new Intent(MLPerfEvaluation.this, SettingsActivity.class);
    startActivityForResult(intent, 0);
  }

  // Animate to highlight newly changed item in the results.
  private boolean animateBackground(RecyclerView.ViewHolder holder) {
    if (holder == null) {
      return true;
    }
    ObjectAnimator bgAnimator =
        ObjectAnimator.ofObject(
            holder.itemView,
            "backgroundColor",
            new ArgbEvaluator(),
            highLightColor,
            backgroundColor);
    bgAnimator.setDuration(4000);
    bgAnimator.start();
    return true;
  }

  // The Animator that uses animateBackground.
  private class ResultItemAnimator extends DefaultItemAnimator {

    @Override
    public boolean animateAdd(RecyclerView.ViewHolder holder) {
      return animateBackground(holder);
    }

    @Override
    public boolean animateChange(
        RecyclerView.ViewHolder oldHolder,
        RecyclerView.ViewHolder newHolder,
        int fromX,
        int fromY,
        int toX,
        int toY) {
      return animateBackground(newHolder);
    }
  }

  // Schedule a inference task with WorkManager for the given model.
  private void scheduleInference(int taskIdx, int modelIdx, String delegate) {
    Log.d(TAG, "scheduleInference " + taskIdx + " , " + modelIdx);
    TaskConfig task = mlperfTasks.getTask(taskIdx);
    final String modelName = task.getModel(modelIdx).getName();
    String outputLogDir = getExternalFilesDir("mlperf/" + modelName).getAbsolutePath();
    Log.i(TAG, "The mlperf log dir for \"" + modelName + "\" is " + outputLogDir + "/");
    RunMLPerfWorker.WorkerData data =
        new RunMLPerfWorker.WorkerData(
            taskIdx, modelIdx, numThreadsPreference, delegate, outputLogDir);
    Message msg = workerHandler.obtainMessage(RunMLPerfWorker.MSG_RUN, data);
    msg.replyTo = replyMessenger;
    workerHandler.sendMessage(msg);
    progressCount.increaseTotal();
    logProgress("Worker for \"" + modelName + "\" with delegate: " + delegate + " scheduled.");
  }

  private static class ProgressCount {

    public ProgressCount(ProgressBar progBar, Window window) {
      totalWorkCount = 0;
      finishedWorkCount = 0;
      progressBar = progBar;
      this.window = window;
    }

    public synchronized void increaseTotal() {
      ++totalWorkCount;
      updateUI();
    }

    public synchronized void decreaseTotal() {
      --totalWorkCount;
      updateUI();
    }

    public synchronized void increaseProgress() {
      ++finishedWorkCount;
      updateUI();
    }

    public void updateUI() {
      if (totalWorkCount > finishedWorkCount) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      }
      if (totalWorkCount == 0) {
        progressBar.setProgress(0);
        return;
      }
      progressBar.setProgress(finishedWorkCount * 100 / totalWorkCount);
    }

    private int totalWorkCount;
    private int finishedWorkCount;
    private final ProgressBar progressBar;
    private final Window window;
  }

  private void addNewResult(ResultHolder result) {
    String key = result.getModel() + result.getRuntime();
    int resultIdx;
    // If a result of (model, runtime) is already displayed, update it.
    if (resultMap.containsKey(key)) {
      resultIdx = resultMap.get(key);
      results.set(resultIdx, result);
      resultAdapter.notifyItemChanged(resultIdx);
    } else {
      resultIdx = results.size();
      resultMap.put(key, resultIdx);
      results.add(result);
      resultAdapter.notifyItemInserted(resultIdx);
    }

    // If scrolling happened, the animation does not run. So call it explicitly in such case.
    resultRecyclerView.clearOnScrollListeners();
    resultRecyclerView.addOnScrollListener(
        new RecyclerView.OnScrollListener() {
          @Override
          public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            RecyclerView.ViewHolder viewHolder =
                recyclerView.findViewHolderForAdapterPosition(resultIdx);
            animateBackground(viewHolder);
            recyclerView.clearOnScrollListeners();
          }
        });
    // Scroll to recently changed item.
    resultRecyclerView.smoothScrollToPosition(resultIdx);
  }

  public Context getActivityContext() {
    return this;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    workerThread.quit();
    Log.d(TAG, "onDestroy() is called.");
  }

  private boolean checkModelIsAvailable() {
    if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        == PackageManager.PERMISSION_GRANTED) {
      return doCheckModelIsAvailable();
    } else {
      ActivityCompat.requestPermissions(
          this, new String[] {android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
      return false;
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    switch (requestCode) {
      case 1:
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          doCheckModelIsAvailable();
        } else {
          logProgress("Warning: You need to grant external storage access to use MLPerf app.");
        }
        break;
      default:
        break;
    }
  }

  private boolean doCheckModelIsAvailable() {
    if (!modelIsAvailable) {
      for (TaskConfig task : mlperfTasks.getTaskList()) {
        for (ModelConfig model : task.getModelList()) {
          if (!new File(MLPerfTasks.getLocalPath(model.getSrc())).canRead()) {
            new ModelExtractTask(MLPerfEvaluation.this, mlperfTasks).execute();
            return false;
          }
        }
        DatasetConfig dataset = task.getDataset();
        if (dataset.hasGroundtruthSrc()
            && !new File(MLPerfTasks.getLocalPath(dataset.getGroundtruthSrc())).canRead()) {
          new ModelExtractTask(MLPerfEvaluation.this, mlperfTasks).execute();
          return false;
        }
      }

      setModelIsAvailable();
    }
    return true;
  }

  private void setModelIsAvailable() {
    modelIsAvailable = true;
    logProgress("Ready. Click the Run button to evaluate.");
  }

  // ModelExtractTask copies or downloads files to their location (external storage) when
  // they're not available.
  private static class ModelExtractTask extends AsyncTask<Void, Void, Void> {
    private final WeakReference<Context> contextRef;
    private final MLPerfConfig mlperfTasks;
    private boolean success = true;
    private String error;

    public ModelExtractTask(Context context, MLPerfConfig mlperfTasks) {
      contextRef = new WeakReference<>(context);
      this.mlperfTasks = mlperfTasks;
    }

    @Override
    protected Void doInBackground(Void... voids) {
      for (TaskConfig task : mlperfTasks.getTaskList()) {
        for (ModelConfig model : task.getModelList()) {
          if (!new File(MLPerfTasks.getLocalPath(model.getSrc())).canRead()) {
            if (!extractFile(model.getSrc())) {
              success = false;
            }
          }
        }
        DatasetConfig dataset = task.getDataset();
        if (!new File(MLPerfTasks.getLocalPath(dataset.getGroundtruthSrc())).canRead()) {
          if (!extractFile(dataset.getGroundtruthSrc())) {
            success = false;
          }
        }
      }
      return null;
    }

    private boolean extractFile(String src) {
      String dest = MLPerfTasks.getLocalPath(src);
      File destFile = new File(dest);
      Log.d(TAG, "Preparing " + destFile.getName());
      destFile.getParentFile().mkdirs();
      // Extract to a temporary file first, so the app can detects if the extraction failed.
      File tmpFile = new File(dest + ".tmp");
      try {
        InputStream in;
        if (src.startsWith(ASSETS_PREFIX)) {
          AssetManager assetManager = ((MLPerfEvaluation) contextRef.get()).getAssets();
          in = assetManager.open(src.substring(ASSETS_PREFIX.length()));
        } else if (src.startsWith("http://") || src.startsWith("https://")) {
          ConnectivityManager cm =
              (ConnectivityManager) contextRef.get().getSystemService(Context.CONNECTIVITY_SERVICE);
          NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
          boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
          if (!isConnected) {
            error = "Error: No network connected.";
            return false;
          }
          in = new URL(src).openStream();
        } else {
          in = new FileInputStream(src);
        }
        OutputStream out = new FileOutputStream(tmpFile, /*append=*/ false);
        copyFile(in, out);
        tmpFile.renameTo(destFile);
      } catch (IOException e) {
        Log.e(TAG, "Failed to prepare file " + dest + ": " + e.getMessage());
        error = "Error: " + e.getMessage();
        return false;
      }

      Log.d(TAG, destFile.getName() + " are extracted.");
      return true;
    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
      byte[] buffer = new byte[1024];
      int read;
      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }
    }

    @Override
    protected void onPreExecute() {
      ((MLPerfEvaluation) contextRef.get()).logProgress("Extracting missing files...");
    }

    @Override
    protected void onPostExecute(Void result) {
      if (success) {
        Log.d(TAG, "All missing files are extracted.");
        ((MLPerfEvaluation) contextRef.get()).logProgress("All missing files are extracted.");
        ((MLPerfEvaluation) contextRef.get()).setModelIsAvailable();
      } else {
        ((MLPerfEvaluation) contextRef.get()).logProgress(error);
      }
    }
  }
}
