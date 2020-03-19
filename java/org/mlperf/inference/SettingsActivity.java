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
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.preference.DialogPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.mlperf.proto.MLPerfConfig;
import org.mlperf.proto.ModelConfig;
import org.mlperf.proto.TaskConfig;

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

      // Add a new ModelsPreference.
      PreferenceScreen screen = getPreferenceScreen();
      ModelsPreference modelsPref = new ModelsPreference(getContext());
      modelsPref.setKey(getString(R.string.models_preference_key));
      modelsPref.setTitle(getString(R.string.preferences_models));
      modelsPref.setSummary(getString(R.string.models_preference_sum));
      modelsPref.setDialogTitle(getString(R.string.preferences_models));
      modelsPref.setIconSpaceReserved(false);
      modelsPref.setPersistent(true);
      screen.addPreference(modelsPref);
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
      if (preference instanceof ModelsPreference) {
        ((ModelsPreference) preference).showDialog(this);
      } else {
        super.onDisplayPreferenceDialog(preference);
      }
    }
  }

  /** Preference for selecting models. */
  private static class ModelsPreference extends DialogPreference {
    public ModelsPreference(Context context) {
      super(context);
      super.setPersistent(false);
      super.setNegativeButtonText(android.R.string.cancel);
      super.setPositiveButtonText(android.R.string.ok);
      setDialogLayoutResource(R.layout.models_pref);
    }

    public void showDialog(Fragment target) {
      ModelsPreferenceDialogFragment fragment =
          ModelsPreferenceDialogFragment.newInstance(
              target.getContext(), getKey(), new HashSet<>(getPersistedStringSet(null)));
      fragment.setTargetFragment(target, 0);
      fragment.show(target.getFragmentManager(), "androidx.preference.PreferenceFragment.DIALOG");
    }

    public void setModels(Set<String> selectedModels) {
      if (selectedModels.equals(getPersistedStringSet(null))) {
        return;
      }
      persistStringSet(selectedModels);
      notifyChanged();
    }
  }

  /** This fragment shows ModelsPreference in a dialog. */
  public static class ModelsPreferenceDialogFragment extends PreferenceDialogFragmentCompat {
    private MLPerfConfig mlperfTasks;
    private Context context;
    private Set<String> selectedModels;

    public static ModelsPreferenceDialogFragment newInstance(
        Context context, String key, Set<String> models) {
      ModelsPreferenceDialogFragment fragment = new ModelsPreferenceDialogFragment();
      fragment.context = context;
      fragment.selectedModels = models;
      fragment.mlperfTasks = MLPerfTasks.getConfig(context);
      Bundle bundle = new Bundle(1);
      bundle.putString(ARG_KEY, key);
      fragment.setArguments(bundle);
      return fragment;
    }

    @Override
    protected void onBindDialogView(View view) {
      super.onBindDialogView(view);
      LinearLayout prefLayout = (LinearLayout) view.findViewById(R.id.container);
      LayoutInflater inflater = LayoutInflater.from(context);
      for (TaskConfig task : mlperfTasks.getTaskList()) {
        // Add a switch to select or deselect all models in a task.
        View taskView = inflater.inflate(R.layout.models_group, prefLayout, false);
        SwitchMaterial taskSwitch = (SwitchMaterial) taskView.findViewById(R.id.tasksSwitch);
        taskSwitch.setText(task.getName());
        ChipGroup modelsGroup = (ChipGroup) taskView.findViewById(R.id.modelsGroup);
        // Add models of that task as chips.
        for (ModelConfig model : task.getModelList()) {
          Chip modelchip = (Chip) inflater.inflate(R.layout.models_item, modelsGroup, false);
          String modelName = model.getName();
          modelchip.setText(model.getTags());
          modelchip.setOnCheckedChangeListener(
              (chipView, isChecked) -> {
                // The switch is on if all models are selected.
                if (isChecked) {
                  taskSwitch.setChecked(true);
                  selectedModels.add(modelName);
                } else {
                  selectedModels.remove(modelName);
                }
                if (modelsGroup.getCheckedChipIds().isEmpty()) {
                  taskSwitch.setChecked(false);
                }
              });
          if (selectedModels.contains(modelName)) {
            modelchip.setChecked(true);
            taskSwitch.setChecked(true);
          }
          modelsGroup.addView(modelchip);
        }
        // Toggle models based on the task switch status.
        taskSwitch.setOnClickListener(
            (switchView) -> {
              if (((SwitchMaterial) switchView).isChecked()) {
                // Set all models of this task as checked.
                for (int idx = 0; idx < modelsGroup.getChildCount(); ++idx) {
                  Chip chip = (Chip) modelsGroup.getChildAt(idx);
                  chip.setChecked(true);
                }
              } else {
                modelsGroup.clearCheck();
              }
            });
        prefLayout.addView(taskView);
      }
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
      if (!positiveResult) {
        return;
      }
      ((ModelsPreference) getPreference()).setModels(selectedModels);
    }
  }
}
