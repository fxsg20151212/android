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
package com.android.tools.idea.gradle.project.build.output

import com.google.common.base.Splitter
import com.intellij.build.output.BuildOutputInstantReader

/**
 * A simple [BuildOutputInstantReader] useful for build output parsing tests.
 *
 * This reader simply takes an input and splits it around any newlines, omitting empty strings,
 * which mimics the behavior of [BuildOutputInstantReaderImpl]
 */
class TestBuildOutputInstantReader(input: String) : BuildOutputInstantReader {
  private val myLines: Iterator<String>

  init {
    myLines = Splitter.on("\n").omitEmptyStrings().split(input).iterator()
  }

  override fun getBuildId(): Any {
    return "Dummy Id"
  }

  override fun readLine(): String? {
    return if (!myLines.hasNext()) {
      null
    }
    else myLines.next()

  }

  override fun pushBack() {
    pushBack(1)
  }

  override fun pushBack(numberOfLines: Int) {
    throw UnsupportedOperationException()
  }

  override fun getCurrentLine(): String {
    throw UnsupportedOperationException()
  }

  override fun append(csq: CharSequence?): BuildOutputInstantReader = this
  override fun append(csq: CharSequence?, start: Int, end: Int): BuildOutputInstantReader = this
  override fun append(c: Char): BuildOutputInstantReader = this
  override fun close() = Unit
}