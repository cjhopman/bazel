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

package com.google.devtools.build.lib.skylarkbuildapi.cpp;

import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkbuildapi.FileApi;
import com.google.devtools.build.lib.skylarkbuildapi.SkylarkActionFactoryApi;
import com.google.devtools.build.lib.skylarkbuildapi.SkylarkRuleContextApi;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.ParamType;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.Runtime.NoneType;
import com.google.devtools.build.lib.syntax.SkylarkList;
import com.google.devtools.build.lib.syntax.SkylarkNestedSet;
import com.google.devtools.build.lib.syntax.StarlarkThread;
import com.google.devtools.build.lib.syntax.Tuple;

/** Utilites related to C++ support. */
@SkylarkModule(
    name = "cc_common",
    doc = "Utilities for C++ compilation, linking, and command line generation.")
public interface BazelCcModuleApi<
        SkylarkActionFactoryT extends SkylarkActionFactoryApi,
        FileT extends FileApi,
        SkylarkRuleContextT extends SkylarkRuleContextApi,
        CcToolchainProviderT extends CcToolchainProviderApi<FeatureConfigurationT>,
        FeatureConfigurationT extends FeatureConfigurationApi,
        CompilationContextT extends CcCompilationContextApi,
        CompilationOutputsT extends CcCompilationOutputsApi<FileT>,
        LinkingOutputsT extends CcLinkingOutputsApi<FileT>,
        LinkerInputT extends LinkerInputApi<LibraryToLinkT, FileT>,
        LibraryToLinkT extends LibraryToLinkApi<FileT>,
        LinkingContextT extends CcLinkingContextApi<FileT>,
        CcToolchainVariablesT extends CcToolchainVariablesApi,
        CcToolchainConfigInfoT extends CcToolchainConfigInfoApi>
    extends CcModuleApi<
        SkylarkActionFactoryT,
        FileT,
        CcToolchainProviderT,
        FeatureConfigurationT,
        CompilationContextT,
        LinkerInputT,
        LinkingContextT,
        LibraryToLinkT,
        CcToolchainVariablesT,
        SkylarkRuleContextT,
        CcToolchainConfigInfoT,
        CompilationOutputsT> {

  @SkylarkCallable(
      name = "compile",
      doc =
          "Should be used for C++ compilation. Returns tuple of "
              + "(<code>CompilationContext</code>, <code>CcCompilationOutputs</code>).",
      useStarlarkThread = true,
      useLocation = true,
      parameters = {
        @Param(
            name = "actions",
            type = SkylarkActionFactoryApi.class,
            positional = false,
            named = true,
            doc = "<code>actions</code> object."),
        @Param(
            name = "feature_configuration",
            doc = "<code>feature_configuration</code> to be queried.",
            positional = false,
            named = true,
            type = FeatureConfigurationApi.class),
        @Param(
            name = "cc_toolchain",
            doc = "<code>CcToolchainInfo</code> provider to be used.",
            positional = false,
            named = true,
            type = CcToolchainProviderApi.class),
        @Param(
            name = "srcs",
            doc = "The list of source files to be compiled.",
            positional = false,
            named = true,
            defaultValue = "[]",
            type = SkylarkList.class),
        @Param(
            name = "public_hdrs",
            doc =
                "List of headers needed for compilation of srcs and may be included by dependent "
                    + "rules transitively.",
            positional = false,
            named = true,
            defaultValue = "[]",
            type = SkylarkList.class),
        @Param(
            name = "private_hdrs",
            doc =
                "List of headers needed for compilation of srcs and NOT to be included by"
                    + " dependent rules.",
            positional = false,
            named = true,
            defaultValue = "[]",
            type = SkylarkList.class),
        @Param(
            name = "includes",
            doc =
                "Search paths for header files referenced both by angle bracket and quotes. "
                    + "Usually passed with -I. Propagated to dependents transitively.",
            positional = false,
            named = true,
            defaultValue = "[]",
            type = SkylarkList.class),
        @Param(
            name = "quote_includes",
            doc =
                "Search paths for header files referenced by quotes, "
                    + "e.g. #include \"foo/bar/header.h\". They can be either relative to the exec "
                    + "root or absolute. Usually passed with -iquote. Propagated to dependents "
                    + "transitively.",
            positional = false,
            named = true,
            defaultValue = "[]",
            type = SkylarkList.class),
        @Param(
            name = "system_includes",
            doc =
                "Search paths for header files referenced by angle brackets, e.g. #include"
                    + " <foo/bar/header.h>. They can be either relative to the exec root or"
                    + " absolute. Usually passed with -isystem. Propagated to dependents "
                    + "transitively.",
            positional = false,
            named = true,
            defaultValue = "[]",
            type = SkylarkList.class),
        @Param(
            name = "framework_includes",
            doc =
                "Search paths for header files from Apple frameworks. They can be either relative "
                    + "to the exec root or absolute. Usually passed with -F. Propagated to "
                    + "dependents transitively.",
            positional = false,
            named = true,
            defaultValue = "[]",
            type = SkylarkList.class),
        @Param(
            name = "defines",
            doc =
                "Set of defines needed to compile this target. Each define is a string. Propagated"
                    + " to dependents transitively.",
            positional = false,
            named = true,
            defaultValue = "[]",
            type = SkylarkList.class),
        @Param(
            name = "local_defines",
            doc =
                "Set of defines needed to compile this target. Each define is a string. Not"
                    + " propagated to dependents transitively.",
            positional = false,
            named = true,
            defaultValue = "[]",
            type = SkylarkList.class),
        @Param(
            name = "user_compile_flags",
            doc = "Additional list of compilation options.",
            positional = false,
            named = true,
            defaultValue = "[]",
            type = SkylarkList.class),
        @Param(
            name = "compilation_contexts",
            doc = "Headers from dependencies used for compilation.",
            positional = false,
            named = true,
            defaultValue = "[]",
            type = SkylarkList.class),
        @Param(
            name = "name",
            doc =
                "This is used for naming the output artifacts of actions created by this "
                    + "method.",
            positional = false,
            named = true,
            type = String.class),
        @Param(
            name = "disallow_pic_outputs",
            doc = "Whether PIC outputs should be created.",
            positional = false,
            named = true,
            defaultValue = "False",
            type = Boolean.class),
        @Param(
            name = "disallow_nopic_outputs",
            doc = "Whether NOPIC outputs should be created.",
            positional = false,
            named = true,
            defaultValue = "False",
            type = Boolean.class),
        @Param(
            name = "additional_inputs",
            doc = "List of additional files needed for compilation of srcs",
            positional = false,
            named = true,
            defaultValue = "[]",
            type = SkylarkList.class),
      })
  Tuple<Object> compile(
      SkylarkActionFactoryT skylarkActionFactoryApi,
      FeatureConfigurationT skylarkFeatureConfiguration,
      CcToolchainProviderT skylarkCcToolchainProvider,
      SkylarkList<?> sources, // <FileT> expected
      SkylarkList<?> publicHeaders, // <FileT> expected
      SkylarkList<?> privateHeaders, // <FileT> expected
      SkylarkList<?> includes, // <String> expected
      SkylarkList<?> quoteIncludes, // <String> expected
      SkylarkList<?> systemIncludes, // <String> expected
      SkylarkList<?> frameworkIncludes, // <String> expected
      SkylarkList<?> defines, // <String> expected
      SkylarkList<?> localDefines, // <String> expected
      SkylarkList<?> userCompileFlags, // <String> expected
      SkylarkList<?> ccCompilationContexts, // <CompilationContextT> expected
      String name,
      boolean disallowPicOutputs,
      boolean disallowNopicOutputs,
      SkylarkList<?> additionalInputs, // <FileT> expected
      Location location,
      StarlarkThread thread)
      throws EvalException, InterruptedException;

  @SkylarkCallable(
      name = "link",
      doc = "Should be used for C++ transitive linking.",
      useStarlarkThread = true,
      useLocation = true,
      parameters = {
        @Param(
            name = "actions",
            type = SkylarkActionFactoryApi.class,
            positional = false,
            named = true,
            doc = "<code>actions</code> object."),
        @Param(
            name = "feature_configuration",
            doc = "<code>feature_configuration</code> to be queried.",
            positional = false,
            named = true,
            type = FeatureConfigurationApi.class),
        @Param(
            name = "cc_toolchain",
            doc = "<code>CcToolchainInfo</code> provider to be used.",
            positional = false,
            named = true,
            type = CcToolchainProviderApi.class),
        @Param(
            name = "compilation_outputs",
            doc = "Compilation outputs containing object files to link.",
            positional = false,
            named = true,
            defaultValue = "None",
            noneable = true,
            allowedTypes = {
              @ParamType(type = CcCompilationOutputsApi.class),
              @ParamType(type = NoneType.class)
            }),
        @Param(
            name = "user_link_flags",
            doc = "Additional list of linker options.",
            positional = false,
            named = true,
            defaultValue = "[]",
            type = SkylarkList.class),
        @Param(
            name = "linking_contexts",
            doc =
                "Linking contexts from dependencies to be linked into the linking context "
                    + "generated by this rule.",
            positional = false,
            named = true,
            defaultValue = "[]",
            type = SkylarkList.class),
        @Param(
            name = "name",
            doc =
                "This is used for naming the output artifacts of actions created by this "
                    + "method.",
            positional = false,
            named = true,
            type = String.class),
        @Param(
            name = "language",
            doc = "Only C++ supported for now. Do not use this parameter.",
            positional = false,
            named = true,
            defaultValue = "'c++'",
            type = String.class),
        @Param(
            name = "output_type",
            doc = "Can be either 'executable' or 'dynamic_library'.",
            positional = false,
            named = true,
            defaultValue = "'executable'",
            type = String.class),
        @Param(
            name = "link_deps_statically",
            doc = " True to link dependencies statically, False dynamically.",
            positional = false,
            named = true,
            defaultValue = "True",
            type = Boolean.class),
        @Param(
            name = "additional_inputs",
            doc = "For additional inputs to the linking action, e.g.: linking scripts.",
            positional = false,
            named = true,
            defaultValue = "[]",
            type = SkylarkList.class),
        @Param(
            name = "grep_includes",
            positional = false,
            named = true,
            noneable = true,
            defaultValue = "None",
            allowedTypes = {@ParamType(type = FileApi.class), @ParamType(type = NoneType.class)}),
      })
  LinkingOutputsT link(
      SkylarkActionFactoryT skylarkActionFactoryApi,
      FeatureConfigurationT skylarkFeatureConfiguration,
      CcToolchainProviderT skylarkCcToolchainProvider,
      Object compilationOutputs,
      SkylarkList<?> userLinkFlags, // <String> expected
      SkylarkList<?> linkingContexts, // <LinkingContextT> expected
      String name,
      String language,
      String outputType,
      boolean linkDepsStatically,
      SkylarkList<?> additionalInputs, // <FileT> expected
      Object grepIncludes,
      Location location,
      StarlarkThread thread)
      throws InterruptedException, EvalException;

  @SkylarkCallable(
      name = "create_compilation_outputs",
      doc = "Create compilation outputs object.",
      useLocation = true,
      parameters = {
        @Param(
            name = "objects",
            doc = "List of object files.",
            positional = false,
            named = true,
            noneable = true,
            defaultValue = "None",
            allowedTypes = {
              @ParamType(type = SkylarkNestedSet.class),
              @ParamType(type = NoneType.class)
            }),
        @Param(
            name = "pic_objects",
            doc = "List of pic object files.",
            positional = false,
            named = true,
            noneable = true,
            defaultValue = "None",
            allowedTypes = {
              @ParamType(type = SkylarkNestedSet.class),
              @ParamType(type = NoneType.class)
            }),
      })
  CompilationOutputsT createCompilationOutputsFromSkylark(
      Object objectsObject, Object picObjectsObject, Location location) throws EvalException;

  @SkylarkCallable(
      name = "merge_compilation_outputs",
      doc = "Merge compilation outputs.",
      parameters = {
        @Param(
            name = "compilation_outputs",
            positional = false,
            named = true,
            defaultValue = "[]",
            type = SkylarkList.class),
      })
  CompilationOutputsT mergeCcCompilationOutputsFromSkylark(
      SkylarkList<?> compilationOutputs) // <CompilationOutputsT> expected
      throws EvalException;
}
