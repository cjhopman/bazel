// Copyright 2018 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.bazel.rules.cpp;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.skylark.SkylarkActionFactory;
import com.google.devtools.build.lib.analysis.skylark.SkylarkRuleContext;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.rules.cpp.CcCompilationContext;
import com.google.devtools.build.lib.rules.cpp.CcCompilationOutputs;
import com.google.devtools.build.lib.rules.cpp.CcLinkingContext;
import com.google.devtools.build.lib.rules.cpp.CcLinkingContext.LinkerInput;
import com.google.devtools.build.lib.rules.cpp.CcLinkingOutputs;
import com.google.devtools.build.lib.rules.cpp.CcModule;
import com.google.devtools.build.lib.rules.cpp.CcToolchainConfigInfo;
import com.google.devtools.build.lib.rules.cpp.CcToolchainProvider;
import com.google.devtools.build.lib.rules.cpp.CcToolchainVariables;
import com.google.devtools.build.lib.rules.cpp.CppSemantics;
import com.google.devtools.build.lib.rules.cpp.FeatureConfigurationForStarlark;
import com.google.devtools.build.lib.rules.cpp.LibraryToLink;
import com.google.devtools.build.lib.skylarkbuildapi.cpp.BazelCcModuleApi;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.SkylarkList;
import com.google.devtools.build.lib.syntax.StarlarkThread;
import com.google.devtools.build.lib.syntax.Tuple;

/**
 * A module that contains Skylark utilities for C++ support.
 *
 * <p>This is a work in progress. The API is guarded behind
 * --experimental_cc_skylark_api_enabled_packages. The API is under development and unstable.
 */
public class BazelCcModule extends CcModule
    implements BazelCcModuleApi<
        SkylarkActionFactory,
        Artifact,
        SkylarkRuleContext,
        CcToolchainProvider,
        FeatureConfigurationForStarlark,
        CcCompilationContext,
        CcCompilationOutputs,
        CcLinkingOutputs,
        LinkerInput,
        LibraryToLink,
        CcLinkingContext,
        CcToolchainVariables,
        CcToolchainConfigInfo> {

  @Override
  public CppSemantics getSemantics() {
    return BazelCppSemantics.INSTANCE;
  }

  @Override
  public Tuple<Object> compile(
      SkylarkActionFactory skylarkActionFactoryApi,
      FeatureConfigurationForStarlark skylarkFeatureConfiguration,
      CcToolchainProvider skylarkCcToolchainProvider,
      SkylarkList<?> sources, // <Artifact> expected
      SkylarkList<?> publicHeaders, // <Artifact> expected
      SkylarkList<?> privateHeaders, // <Artifact> expected
      SkylarkList<?> includes, // <String> expected
      SkylarkList<?> quoteIncludes, // <String> expected
      SkylarkList<?> systemIncludes, // <String> expected
      SkylarkList<?> frameworkIncludes, // <String> expected
      SkylarkList<?> defines, // <String> expected
      SkylarkList<?> localDefines, // <String> expected
      SkylarkList<?> userCompileFlags, // <String> expected
      SkylarkList<?> ccCompilationContexts, // <CcCompilationContext> expected
      String name,
      boolean disallowPicOutputs,
      boolean disallowNopicOutputs,
      SkylarkList<?> additionalInputs, // <Artifact> expected
      Location location,
      StarlarkThread thread)
      throws EvalException, InterruptedException {
    return compile(
        skylarkActionFactoryApi,
        skylarkFeatureConfiguration,
        skylarkCcToolchainProvider,
        sources,
        publicHeaders,
        privateHeaders,
        includes,
        quoteIncludes,
        systemIncludes,
        frameworkIncludes,
        defines,
        localDefines,
        userCompileFlags,
        ccCompilationContexts,
        name,
        disallowPicOutputs,
        disallowNopicOutputs,
        /* grepIncludes= */ null,
        /* headersForClifDoNotUseThisParam= */ ImmutableList.of(),
        SkylarkList.createImmutable(
            additionalInputs.getContents(Artifact.class, "additional_inputs")),
        location,
        /* thread= */ null);
  }

  @Override
  public CcLinkingOutputs link(
      SkylarkActionFactory actions,
      FeatureConfigurationForStarlark skylarkFeatureConfiguration,
      CcToolchainProvider skylarkCcToolchainProvider,
      Object compilationOutputs,
      SkylarkList<?> userLinkFlags, // <String> expected
      SkylarkList<?> linkingContexts, // <CcLinkingContext> expected
      String name,
      String language,
      String outputType,
      boolean linkDepsStatically,
      SkylarkList<?> additionalInputs, // <Artifact> expected
      Object grepIncludes,
      Location location,
      StarlarkThread thread)
      throws InterruptedException, EvalException {
    return super.link(
        actions,
        skylarkFeatureConfiguration,
        skylarkCcToolchainProvider,
        convertFromNoneable(compilationOutputs, /* defaultValue= */ null),
        userLinkFlags,
        linkingContexts,
        name,
        language,
        outputType,
        linkDepsStatically,
        additionalInputs,
        /* grepIncludes= */ null,
        location,
        thread);
  }

  @Override
  public CcCompilationOutputs createCompilationOutputsFromSkylark(
      Object objectsObject, Object picObjectsObject, Location location) throws EvalException {
    return super.createCompilationOutputsFromSkylark(objectsObject, picObjectsObject, location);
  }

  @Override
  public CcCompilationOutputs mergeCcCompilationOutputsFromSkylark(
      SkylarkList<?> compilationOutputs) throws EvalException {
    CcCompilationOutputs.Builder ccCompilationOutputsBuilder = CcCompilationOutputs.builder();
    for (CcCompilationOutputs ccCompilationOutputs :
        compilationOutputs.getContents(CcCompilationOutputs.class, "compilation_outputs")) {
      ccCompilationOutputsBuilder.merge(ccCompilationOutputs);
    }
    return ccCompilationOutputsBuilder.build();
  }
}
