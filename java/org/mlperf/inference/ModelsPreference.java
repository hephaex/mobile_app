/* Copyright 2020 The MLPerf Authors. All Rights Reserved.

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
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import androidx.fragment.app.Fragment;
import androidx.preference.DialogPreference;
import androidx.preference.PreferenceDialogFragmentCompat;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.util.HashSet;
import java.util.Set;
import org.mlperf.proto.MLPerfConfig;
import org.mlperf.proto.ModelConfig;
import org.mlperf.proto.TaskConfig;

/** Preference for selecting models. */
public class ModelsPreference extends DialogPreference {
  public ModelsPreference(Context context) {
    super(context);
    init();
  }

  public ModelsPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  private void init() {
    super.setPersistent(true);
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
