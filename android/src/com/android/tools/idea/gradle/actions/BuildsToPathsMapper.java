/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.actions;

import com.android.build.OutputFile;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.AppBundleProjectBuildOutput;
import com.android.builder.model.AppBundleVariantBuildOutput;
import com.android.builder.model.InstantAppProjectBuildOutput;
import com.android.builder.model.InstantAppVariantBuildOutput;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.run.OutputBuildAction;
import com.android.tools.idea.gradle.run.PostBuildModel;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ImmutableList;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <p> Generates a map from module/build variant to the location
 * (if it's apk, either the apk itself if only one or to the folder if multiples.
 * if it's app bundle, it's always bundle itself.)
 * <p>
 * {@link PostBuildModel} being built from the result of {@link OutputBuildAction} contains paths information of each of the build.
 */
public class BuildsToPathsMapper {
  @NotNull
  public static BuildsToPathsMapper getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, BuildsToPathsMapper.class);
  }

  @NotNull
  Map<String, File> getBuildsToPaths(@Nullable Object model,
                                     @NotNull List<String> buildVariants,
                                     @NotNull Collection<Module> modules,
                                     boolean isAppBundle) {
    boolean isSigned = !buildVariants.isEmpty();
    if (isSigned) {
      assert modules.size() == 1;
    }

    PostBuildModel postBuildModel = null;
    TreeMap<String, File> buildsToPathsCollector = new TreeMap<>();
    if (model instanceof OutputBuildAction.PostBuildProjectModels) {
      postBuildModel = new PostBuildModel((OutputBuildAction.PostBuildProjectModels)model);
    }

    for (Module module : modules) {
      AndroidModuleModel androidModel = AndroidModuleModel.get(module);
      if (androidModel == null) {
        continue;
      }

      if (!isSigned) {
        buildVariants = ImmutableList.singleton(androidModel.getSelectedVariant().getName());
      }

      for (String buildVariant : buildVariants) {
        collectBuildsToPaths(androidModel, postBuildModel, module, buildVariant, buildsToPathsCollector, isAppBundle, isSigned);
      }
    }

    return buildsToPathsCollector;
  }

  private static void collectBuildsToPaths(@NotNull AndroidModuleModel androidModel,
                                           @Nullable PostBuildModel postBuildModel,
                                           @NotNull Module module,
                                           @NotNull String buildVariant,
                                           @NotNull Map<String, File> buildsToPathsCollector,
                                           boolean isAppBundle,
                                           boolean isSigned) {
    File outputFolderOrFile = null;

    if (postBuildModel != null) {

      if (androidModel.getAndroidProject().getProjectType() == AndroidProject.PROJECT_TYPE_APP) {
        if (isAppBundle) {
          outputFolderOrFile = tryToGetOutputPostBuildBundleFile(module, postBuildModel, buildVariant);
        }
        else {
          outputFolderOrFile = tryToGetOutputPostBuildApkFile(module, postBuildModel, buildVariant);
        }
      }
      else if (androidModel.getAndroidProject().getProjectType() == AndroidProject.PROJECT_TYPE_INSTANTAPP) {
        outputFolderOrFile = tryToGetOutputPostBuildInstantApp(module, postBuildModel, buildVariant);
      }
    }

    if (outputFolderOrFile == null && !isSigned && !isAppBundle) {
      outputFolderOrFile = tryToGetOutputPreBuild(androidModel);
    }

    if (outputFolderOrFile == null) {
      return;
    }

    buildsToPathsCollector.put(isSigned ? buildVariant : module.getName(), outputFolderOrFile);
  }

  @Nullable
  private static File tryToGetOutputPostBuildApkFile(@NotNull Module module,
                                                     @NotNull PostBuildModel postBuildModel,
                                                     @NotNull String buildVariant) {
    ProjectBuildOutput projectBuildOutput = postBuildModel.findProjectBuildOutput(module);
    if (projectBuildOutput == null) {
      return null;
    }

    for (VariantBuildOutput variantBuildOutput : projectBuildOutput.getVariantsBuildOutput()) {
      if (variantBuildOutput.getName().equals(buildVariant)) {
        Collection<OutputFile> outputs = variantBuildOutput.getOutputs();
        File outputFolderOrApk = outputs.iterator().next().getOutputFile();
        if (outputs.size() > 1) {
          return outputFolderOrApk.getParentFile();
        }
        return outputFolderOrApk;
      }
    }

    return null;
  }

  @Nullable
  private static File tryToGetOutputPostBuildBundleFile(@NotNull Module module,
                                                        @NotNull PostBuildModel postBuildModel,
                                                        @NotNull String buildVariant) {
    AppBundleProjectBuildOutput appBundleProjectBuildOutput = postBuildModel.findAppBundleProjectBuildOutput(module);
    if (appBundleProjectBuildOutput == null) {
      return null;
    }

    for (AppBundleVariantBuildOutput variantBuildOutput : appBundleProjectBuildOutput.getAppBundleVariantsBuildOutput()) {
      if (variantBuildOutput.getName().equals(buildVariant)) {
        return variantBuildOutput.getBundleFile();
      }
    }

    return null;
  }

  @Nullable
  private static File tryToGetOutputPostBuildInstantApp(@NotNull Module module,
                                                        @NotNull PostBuildModel postBuildModel,
                                                        @NotNull String buildVariant) {
    InstantAppProjectBuildOutput instantAppProjectBuildOutput = postBuildModel.findInstantAppProjectBuildOutput(module);
    if (instantAppProjectBuildOutput == null) {
      return null;
    }

    for (InstantAppVariantBuildOutput variantBuildOutput : instantAppProjectBuildOutput.getInstantAppVariantsBuildOutput()) {
      if (variantBuildOutput.getName().equals(buildVariant)) {
        return variantBuildOutput.getOutput().getOutputFile();
      }
    }

    return null;
  }

  @Nullable
  private static File tryToGetOutputPreBuild(@NotNull AndroidModuleModel androidModel) {
    Collection<AndroidArtifactOutput> outputs = androidModel.getMainArtifact().getOutputs();
    File outputFolderOrApk = outputs.iterator().next().getOutputFile();
    if (outputs.size() > 1) {
      return outputFolderOrApk.getParentFile();
    }
    return outputFolderOrApk;
  }
}