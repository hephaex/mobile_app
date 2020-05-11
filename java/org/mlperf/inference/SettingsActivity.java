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
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On handset devices,
 * settings are presented as a single list. On tablets, settings are split by category, with
 * category headers shown to the left of the list of settings.
 *
 * <p>See <a href="http://developer.android.com/design/patterns/settings.html">Android Design:
 * Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings API Guide</a> for more
 * information on developing a Settings UI.
 */
public class SettingsActivity extends AppCompatActivity {
  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_setting);
    getSupportFragmentManager()
        .beginTransaction()
        .replace(R.id.settings_container, new MLPerfPreferenceFragment())
        .commit();
  }

  /** This fragment shows preferences only. */
  public static class MLPerfPreferenceFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
      setPreferencesFromResource(R.xml.pref_model, rootKey);

      // Only allow inputting number in the preference for number of threads.
      EditTextPreference numThreadsPreference =
          getPreferenceManager().findPreference(getString(R.string.num_threads_key));
      numThreadsPreference.setOnBindEditTextListener(
          new EditTextPreference.OnBindEditTextListener() {
            @Override
            public void onBindEditText(@NonNull EditText editText) {
              editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            }
          });

      // Add NNAPI options to delegate setttings.
      MultiSelectListPreference delegatePreference =
          getPreferenceManager().findPreference(getString(R.string.pref_delegate_key));
      ArrayList<String> devices = MLPerfDriverWrapper.listDevicesForNNAPI();
      ArrayList<CharSequence> entries =
          new ArrayList<>(Arrays.asList(delegatePreference.getEntries()));
      ArrayList<CharSequence> entryValues =
          new ArrayList<>(Arrays.asList(delegatePreference.getEntryValues()));
      if (devices.isEmpty()) {
        entries.add(getString(R.string.delegate_nnapi));
        entryValues.add(getString(R.string.delegate_nnapi));
      } else {
        for (String device : devices) {
          entries.add("NNAPI (" + device + ")");
          entryValues.add("NNAPI-" + device);
        }
      }
      delegatePreference.setEntries(entries.toArray(new CharSequence[entries.size()]));
      delegatePreference.setEntryValues(entryValues.toArray(new CharSequence[entryValues.size()]));

      // Load custom configuration.
      Context context = getPreferenceManager().getContext();
      SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
      Preference customConfig = (Preference) findPreference(getString(R.string.custom_config_key));
      customConfig.setOnPreferenceClickListener(
          new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
              Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
              intent.setType("*/*");
              startActivityForResult(intent, 0);
              return true;
            }
          });
      String configSummary = sharedPref.getString(getString(R.string.config_summary_key), null);
      if (configSummary != null) {
        customConfig.setSummary(configSummary);
      }

      // Set visibility for backend setting groups.
      String selected_backend =
          sharedPref.getString(
              getString(R.string.backend_preference_key),
              getString(R.string.tflite_preference_key));
      setBackendSettingsVisibility(selected_backend);
      getPreferenceManager()
          .findPreference(getString(R.string.backend_preference_key))
          .setOnPreferenceChangeListener(
              (pref, value) -> {
                setBackendSettingsVisibility((String) value);
                return true;
              });
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
      if (preference instanceof ModelsPreference) {
        ((ModelsPreference) preference).showDialog(this);
      } else {
        super.onDisplayPreferenceDialog(preference);
      }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
      super.onActivityResult(requestCode, resultCode, data);
      Context context = getPreferenceManager().getContext();
      try {
        InputStream is = context.getContentResolver().openInputStream(data.getData());
        BufferedReader buffreader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        String line;
        StringBuilder stringBuilder = new StringBuilder();
        while ((line = buffreader.readLine()) != null) {
          stringBuilder.append(line);
          stringBuilder.append('\n');
        }
        String text = stringBuilder.toString();
        if (MLPerfTasks.loadCustomConfig(text)) {
          SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
          SharedPreferences.Editor editor = preferences.edit();
          // Store the file content instead of the file path because the app only has read access
          // to this file via Uri inside onActivityResult. This means whenever the file is edited,
          // it needs to be re-selected again.
          editor.putString(getString(R.string.custom_config_key), text);
          editor.putString(getString(R.string.config_summary_key), data.getData().getPath());
          editor.commit();
          Preference customConfig =
              (Preference) findPreference(getString(R.string.custom_config_key));
          customConfig.setSummary(data.getData().getPath());
        }
      } catch (Exception e) {
        Log.e("Setting", "Failed to read text config file: " + e.getMessage());
      }
    }

    // Set preference group of selected backend to be visible.
    private void setBackendSettingsVisibility(String selected_backend) {
      Context context = getPreferenceManager().getContext();
      String[] backend_list = context.getResources().getStringArray(R.array.backend_value);
      for (String backend : backend_list) {
        if (backend.equals(selected_backend)) {
          getPreferenceManager().findPreference(backend).setVisible(true);
        } else {
          getPreferenceManager().findPreference(backend).setVisible(false);
        }
      }
    }
  }
}
