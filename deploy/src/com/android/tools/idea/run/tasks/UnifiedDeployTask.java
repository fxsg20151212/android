/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.idea.run.tasks;

import com.android.ddmlib.IDevice;
import com.android.tools.deploy.swapper.DexArchiveDatabase;
import com.android.tools.deploy.swapper.SQLiteDexArchiveDatabase;
import com.android.tools.deploy.swapper.WorkQueueDexArchiveDatabase;
import com.android.tools.deployer.AdbClient;
import com.android.tools.deployer.ApkDiffer;
import com.android.tools.deployer.Deployer;
import com.android.tools.deployer.Installer;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnifiedDeployTask implements LaunchTask {

  public static final int MIN_API_VERSION = 27;

  public enum DeployType {
    // When there is no previous APK Install.
    INSTALL("Install"),

    // Only update Java classes. No resource change, now activity restarts.
    CODE_SWAP("Code swap"),

    // Everything, including resource changes.
    FULL_SWAP("Code and resource swap");

    @NotNull
    private final String myName;

    DeployType(@NotNull String name) {
      myName = name;
    }

    @NotNull
    public String getName() {
      return myName;
    }
  }

  private static final String ID = "UNIFIED_DEPLOY";

  private static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.toolWindowGroup("UnifiedDeployTask", ToolWindowId.RUN);

  @NotNull private final Project myProject;

  @NotNull private final Collection<ApkInfo> myApks;

  @Nullable private String myFailureReason;

  // TODO: Move this to an an application component.
  private static DexArchiveDatabase myDb = new WorkQueueDexArchiveDatabase(new SQLiteDexArchiveDatabase(
    new File(Paths.get(PathManager.getSystemPath(), ".deploy.db").toString())));

  public static final Logger LOG = Logger.getInstance(UnifiedDeployTask.class);

  private final DeployType type;

  /**
   * Creates a task to deploy a list of apks.
   *
   * @param project         the project that this task is running within.
   * @param apks            the apks to deploy.
   * @param swap            whether to perform swap on a running app or to just install and restart.
   * @param activityRestart whether to restart activity upon swap.
   */
  public UnifiedDeployTask(@NotNull Project project, @NotNull Collection<ApkInfo> apks, DeployType type) {
    myProject = project;
    myApks = apks;
    this.type = type;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Installing APK";
  }

  @Nullable
  @Override
  public String getFailureReason() {
    return myFailureReason;
  }

  @Override
  public int getDuration() {
    return 20;
  }

  private String getLocalInstaller() {
    File path = new File(PathManager.getHomePath(), "plugins/android/resources/installer");
    if (!path.exists()) {
      // Development mode
      path = new File(PathManager.getHomePath(), "../../bazel-bin/tools/base/deploy/installer/android");
    }
    return path.getAbsolutePath();
  }

  @Override
  public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    for (ApkInfo apk : myApks) {
      LOG.info("Processing application:" + apk.getApplicationId());

      List<String> paths = apk.getFiles().stream().map(apkunit -> apkunit.getApkFile().getPath()).collect(Collectors.toList());
      LogWrapper logger = new LogWrapper(LOG);
      AdbClient adb = new AdbClient(device, logger);
      Installer installer = new Installer(getLocalInstaller(), adb, logger);
      Deployer deployer = new Deployer(apk.getApplicationId(), paths, adb, myDb, installer, logger);
      Deployer.RunResponse response;
      try {
        switch (type) {
          case INSTALL:
            response = deployer.install();
            break;
          case CODE_SWAP:
            response = deployer.codeSwap();
            break;
          case FULL_SWAP:
            response = deployer.fullSwap();
            break;
          default:
            throw new UnsupportedOperationException("Not supported deployment type");
        }
      }
      catch (IOException e) {
        myFailureReason = "Error deploying APK";
        LOG.error("Error deploying APK", e);
        return false;
      }

      if (response.status == Deployer.RunResponse.Status.ERROR) {
        myFailureReason = "Error during deployment \"" + response.errorMessage + "\"";
        LOG.info(response.errorMessage);
        return false;
      }

      if (response.status == Deployer.RunResponse.Status.NOT_INSTALLED) {
        // TODO: Skip code swap and resource swap altogether.
        // Save localApk using localApkHash key.
        for (String apkAnalysisKey : response.result.keySet()) {
          Deployer.RunResponse.Analysis analysis = response.result.get(apkAnalysisKey);
          LOG.info("Apk: " + apkAnalysisKey);
          LOG.info("    local apk id: " + analysis.localApkHash);
        }
        continue;
      }

      // For each APK, a diff, a local if and a remote id were generated.
      for (String apkAnalysisKey : response.result.keySet()) {
        // TODO: Analysis diff, see if resource or code swap are needed. Use local and remote hash as key
        // to query the apk database.
        Deployer.RunResponse.Analysis analysis = response.result.get(apkAnalysisKey);
        LOG.info("Apk: " + apkAnalysisKey);
        LOG.info("    local apk id: " + analysis.localApkHash);
        LOG.info("    remot apk id: " + analysis.remoteApkHash);

        for (Map.Entry<String, ApkDiffer.ApkEntryStatus> statusEntry : analysis.diffs.entrySet()) {
          LOG.info("  " + statusEntry.getKey() +
                             " [" + statusEntry.getValue().toString().toLowerCase() + "]");
        }
      }

      NOTIFICATION_GROUP.createNotification(type.getName() + " successful", NotificationType.INFORMATION)
                        .setImportant(false).notify(myProject);
    }

    return true;
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }
}
