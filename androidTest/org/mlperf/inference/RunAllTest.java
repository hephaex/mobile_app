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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.content.SharedPreferences;
import android.view.View;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.InstrumentationRegistry;
import androidx.test.espresso.PerformException;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.GrantPermissionRule;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for running all models. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class RunAllTest {

  private SharedPreferences sharedPref;

  /**
   * Use {@link ActivityScenarioRule} to create and launch the activity under test, and close it
   * after test completes. This is a replacement for {@link androidx.test.rule.ActivityTestRule}.
   */
  @Rule
  public ActivityScenarioRule<MLPerfEvaluation> activityScenarioRule =
      new ActivityScenarioRule<>(MLPerfEvaluation.class);

  @Rule
  public GrantPermissionRule permissionRule =
      GrantPermissionRule.grant(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);

  @Test
  public void testRunAll() {
    // Get number of selected models from preference.
    sharedPref =
        PreferenceManager.getDefaultSharedPreferences(InstrumentationRegistry.getTargetContext());
    int num_models = sharedPref.getStringSet("selected_models", null).size();

    // Click the play button then wait for all tasks finished.
    onView(withId(R.id.action_play)).perform(click());
    onView(withId(R.id.results_recycler_view))
        .perform(WaitForViewsAction.waitFor(withRecyclerViewSize(num_models), 6000 * 1000));
    // Click the refresh button.
    onView(withId(R.id.action_refresh)).perform(click());
    onView(withId(R.id.results_recycler_view))
        .perform(WaitForViewsAction.waitFor(withRecyclerViewSize(0), 1000));
  }

  public static class WaitForViewsAction implements ViewAction {

    private final Matcher<View> viewMatcher;
    private final long timeoutMs;

    public WaitForViewsAction(Matcher<View> viewMatcher, long timeout) {
      this.viewMatcher = viewMatcher;
      this.timeoutMs = timeout;
    }

    @Override
    public Matcher<View> getConstraints() {
      return ViewMatchers.isDisplayed();
    }

    @Override
    public String getDescription() {
      return "wait until all elements show up";
    }

    @Override
    public void perform(UiController controller, View view) {
      controller.loopMainThreadUntilIdle();
      final long startTime = System.currentTimeMillis();
      final long endTime = startTime + timeoutMs;

      while (System.currentTimeMillis() < endTime) {
        if (viewMatcher.matches(view)) {
          return;
        }

        controller.loopMainThreadForAtLeast(100);
      }

      // Timeout.
      throw new PerformException.Builder()
          .withActionDescription(getDescription())
          .withViewDescription(viewMatcher.toString())
          .build();
    }

    public static ViewAction waitFor(Matcher<View> viewMatcher, long timeout) {
      return new WaitForViewsAction(viewMatcher, timeout);
    }
  }

  public static Matcher<View> withRecyclerViewSize(final int size) {
    return new TypeSafeMatcher<View>() {

      @Override
      public boolean matchesSafely(final View view) {
        final int actualListSize = ((RecyclerView) view).getAdapter().getItemCount();
        return actualListSize == size;
      }

      @Override
      public void describeTo(final Description description) {
        description.appendText("RecyclerView should have " + size + " items");
      }
    };
  }
}
