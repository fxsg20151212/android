/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.tests.gui.debugger;

import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.android.tools.idea.tests.util.NotMatchingPatternMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(GuiTestRunner.class)
public class BasicNativeDebuggerTest extends DebuggerTestBase {

  @Rule public final NativeDebuggerGuiTestRule guiTest = new NativeDebuggerGuiTestRule();

  @Test
  @RunIn(TestGroup.QA_UNRELIABLE)
  public void testSessionRestart() throws IOException, ClassNotFoundException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("BasicJniApp");
    createDefaultAVD(guiTest.ideFrame().invokeAvdManager());
    final IdeFrameFixture projectFrame = guiTest.ideFrame();

    // Setup breakpoints
    openAndToggleBreakPoints(projectFrame,
                             "app/src/main/jni/multifunction-jni.c",
                             "return (*env)->NewStringUTF(env, message);");

    projectFrame.debugApp(DEBUG_CONFIG_NAME)
      .selectDevice(AVD_NAME)
      .clickOk();

    DebugToolWindowFixture debugToolWindowFixture = new DebugToolWindowFixture(projectFrame);
    waitForSessionStart(debugToolWindowFixture);

    projectFrame.findDebugApplicationButton().click();

    MessagesFixture errorMessage = MessagesFixture.findByTitle(guiTest.robot(), "Launching " + DEBUG_CONFIG_NAME);
    errorMessage.requireMessageContains("Restart App").click("Restart " + DEBUG_CONFIG_NAME);

    DeployTargetPickerDialogFixture deployTargetPicker = DeployTargetPickerDialogFixture.find(guiTest.robot());
    deployTargetPicker.selectDevice(AVD_NAME).clickOk();

    waitUntilDebugConsoleCleared(debugToolWindowFixture);
    waitForSessionStart(debugToolWindowFixture);
    stopDebugSession(debugToolWindowFixture);
  }

  @Test
  @RunIn(TestGroup.QA_UNRELIABLE)
  public void testMultiBreakAndResume() throws IOException, ClassNotFoundException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("BasicJniApp");
    createDefaultAVD(guiTest.ideFrame().invokeAvdManager());
    final IdeFrameFixture projectFrame = guiTest.ideFrame();

    openAndToggleBreakPoints(projectFrame,
                             "app/src/main/jni/multifunction-jni.c",
                             "return sum;",
                             "return product;",
                             "return quotient;",
                             "return (*env)->NewStringUTF(env, message);"
    );

    // Setup the expected patterns to match the variable values displayed in Debug windows's 'Variables' tab.
    String[][] expectedPatterns = {
      {
        variableToSearchPattern("x1", "int", "1"),
        variableToSearchPattern("x2", "int", "2"),
        variableToSearchPattern("x3", "int", "3"),
        variableToSearchPattern("x4", "int", "4"),
        variableToSearchPattern("x5", "int", "5"),
        variableToSearchPattern("x6", "int", "6"),
        variableToSearchPattern("x7", "int", "7"),
        variableToSearchPattern("x8", "int", "8"),
        variableToSearchPattern("x9", "int", "9"),
        variableToSearchPattern("x10", "int", "10"),
        variableToSearchPattern("sum", "int", "55"),
      },
      {
        variableToSearchPattern("x1", "int", "1"),
        variableToSearchPattern("x2", "int", "2"),
        variableToSearchPattern("x3", "int", "3"),
        variableToSearchPattern("x4", "int", "4"),
        variableToSearchPattern("x5", "int", "5"),
        variableToSearchPattern("x6", "int", "6"),
        variableToSearchPattern("x7", "int", "7"),
        variableToSearchPattern("x8", "int", "8"),
        variableToSearchPattern("x9", "int", "9"),
        variableToSearchPattern("x10", "int", "10"),
        variableToSearchPattern("product", "int", "3628800"),
      },
      {
        variableToSearchPattern("x1", "int", "1024"),
        variableToSearchPattern("x2", "int", "2"),
        variableToSearchPattern("quotient", "int", "512"),
      },
      {
        variableToSearchPattern("sum_of_10_ints", "int", "55"),
        variableToSearchPattern("product_of_10_ints", "int", "3628800"),
        variableToSearchPattern("quotient", "int", "512")
      }
    };

    projectFrame.debugApp(DEBUG_CONFIG_NAME)
      .selectDevice(AVD_NAME)
      .clickOk();

    DebugToolWindowFixture debugToolWindowFixture = new DebugToolWindowFixture(projectFrame);
    waitForSessionStart(debugToolWindowFixture);

    checkBreakPointsAreHit(projectFrame, expectedPatterns);

    stopDebugSession(debugToolWindowFixture);
  }

  /**
   * Verifies that instant run hot swap works as expected on a C++ support project.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14603479
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import BasicJniApp.
   *   2. Select auto debugger on Edit Configurations dialog.
   *   3. Set breakpoints both in Java and C++ code.
   *   4. Debug on a device running M or earlier.
   *   5. When the C++ breakpoint is hit, verify variables and resume
   *   6. When the Java breakpoint is hit, verify variables
   *   7. Stop debugging
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.QA_UNRELIABLE)
  public void testCAndJavaBreakAndResume() throws Exception {
    guiTest.importProjectAndWaitForProjectSyncToFinish("BasicJniApp");
    createDefaultAVD(guiTest.ideFrame().invokeAvdManager());
    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    ideFrameFixture.invokeMenuPath("Run", "Edit Configurations...");
    EditConfigurationsDialogFixture.find(guiTest.robot())
      .selectAutoDebugger()
      .clickOk();

    // Setup C++ and Java breakpoints.
    openAndToggleBreakPoints(ideFrameFixture, "app/src/main/jni/multifunction-jni.c", "return (*env)->NewStringUTF(env, message);");
    openAndToggleBreakPoints(ideFrameFixture, "app/src/main/java/com/example/BasicJniApp.java", "setContentView(tv);");

    // Setup the expected patterns to match the variable values displayed in Debug windows's 'Variables' tab.
    String[][] expectedPatterns = {
      {
        variableToSearchPattern("sum_of_10_ints", "int", "55"),
        variableToSearchPattern("product_of_10_ints", "int", "3628800"),
        variableToSearchPattern("quotient", "int", "512"),
      },
      {
        variableToSearchPattern("s", "\"Success. Sum = 55, Product = 3628800, Quotient = 512\""),
      },
    };

    ideFrameFixture.debugApp(DEBUG_CONFIG_NAME)
      .selectDevice(AVD_NAME)
      .clickOk();

    DebugToolWindowFixture debugToolWindowFixture = new DebugToolWindowFixture(ideFrameFixture);
    waitForSessionStart(debugToolWindowFixture);

    checkBreakPointsAreHit(ideFrameFixture, expectedPatterns);
  }

  private void waitUntilDebugConsoleCleared(DebugToolWindowFixture debugToolWindowFixture) {
    final ExecutionToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(DEBUG_CONFIG_NAME);
    contentFixture.waitForOutput(new NotMatchingPatternMatcher(DEBUGGER_ATTACHED_PATTERN), 10);
  }

}
