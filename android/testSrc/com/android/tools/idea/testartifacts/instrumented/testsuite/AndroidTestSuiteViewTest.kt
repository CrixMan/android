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
package com.android.tools.idea.testartifacts.instrumented.testsuite

import com.android.testutils.MockitoKt.eq
import com.intellij.execution.process.ProcessHandler
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

/**
 * Unit tests for [AndroidTestSuiteView].
 */
class AndroidTestSuiteViewTest {

  @Mock lateinit var processHandler: ProcessHandler

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)
  }

  @Test
  fun attachToProcess() {
    val view = AndroidTestSuiteView()

    view.attachToProcess(processHandler)

    verify(processHandler).putCopyableUserData(eq(ANDROID_TEST_RESULT_LISTENER_KEY), eq(view))
  }
}