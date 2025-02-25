// Copyright 2019 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.analysis.skylark;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.CommandLine;
import com.google.devtools.build.lib.actions.CommandLineExpansionException;
import com.google.devtools.build.lib.actions.CommandLines.CommandLineAndParamFileInfo;
import com.google.devtools.build.lib.actions.ParamFileInfo;
import com.google.devtools.build.lib.actions.ParameterFile.ParameterFileType;
import com.google.devtools.build.lib.actions.SingleStringArgFormatter;
import com.google.devtools.build.lib.analysis.skylark.SkylarkCustomCommandLine.ScalarArg;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkbuildapi.CommandLineArgsApi;
import com.google.devtools.build.lib.skylarkinterface.SkylarkPrinter;
import com.google.devtools.build.lib.syntax.BaseFunction;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.FunctionSignature;
import com.google.devtools.build.lib.syntax.Mutability;
import com.google.devtools.build.lib.syntax.Runtime;
import com.google.devtools.build.lib.syntax.SkylarkList;
import com.google.devtools.build.lib.syntax.SkylarkNestedSet;
import com.google.devtools.build.lib.syntax.Starlark;
import com.google.devtools.build.lib.syntax.StarlarkMutable;
import com.google.devtools.build.lib.syntax.StarlarkSemantics;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Implementation of the {@code Args} Starlark type, which, in a builder-like pattern, encapsulates
 * the data needed to build all or part of a command line.
 */
public abstract class Args extends StarlarkMutable implements CommandLineArgsApi {

  private Args() {
    // Ensure Args subclasses are defined only in this file.
  }

  @Override
  public void repr(SkylarkPrinter printer) {
    printer.append("context.args() object");
  }

  @Override
  public void debugPrint(SkylarkPrinter printer) {
    try {
      printer.append(Joiner.on(" ").join(build().arguments()));
    } catch (CommandLineExpansionException e) {
      printer.append("Cannot expand command line: " + e.getMessage());
    }
  }

  /**
   * Returns the file format to use if this object's encapsulated arguments were to be written to a
   * param file. This value is meaningful even if {@link #getParamFileInfo} is null, as one can
   * force these args to be written to a param file using {@code actions.write}, even if the args
   * would not be written to a params file if used in normal action registration.
   */
  public abstract ParameterFileType getParameterFileType();

  /**
   * Returns a {@link ParamFileInfo} describing how a params file should be constructed to contain
   * this object's encapsulated arguments when an action is registered using this object. If a
   * parameter file should not be used (even under operating system arg limits), returns null.
   */
  @Nullable
  public abstract ParamFileInfo getParamFileInfo();

  /**
   * Returns a set of directory artifacts which will need to be expanded for evaluating the
   * encapsulated arguments during execution.
   */
  public abstract ImmutableSet<Artifact> getDirectoryArtifacts();

  /** Returns the command line built by this {@link Args} object. */
  public abstract CommandLine build();

  /**
   * Returns a frozen {@link Args} representation corresponding to an already-registered action.
   *
   * @param commandLineAndParamFileInfo the command line / ParamFileInfo pair that this Args should
   *     represent
   * @param directoryInputs a set containing all directory artifacts of the action; {@link
   *     Artifact#isDirectory()} must be true for each artifact in the set
   */
  public static Args forRegisteredAction(
      CommandLineAndParamFileInfo commandLineAndParamFileInfo,
      ImmutableSet<Artifact> directoryInputs) {
    return new FrozenArgs(
        commandLineAndParamFileInfo.commandLine,
        commandLineAndParamFileInfo.paramFileInfo,
        directoryInputs);
  }

  /** Creates and returns a new (empty) {@link Args} object. */
  public static Args newArgs(@Nullable Mutability mutability, StarlarkSemantics starlarkSemantics) {
    return new MutableArgs(mutability, starlarkSemantics);
  }

  /**
   * A frozen (immutable) representation of {@link Args}, constructed from an already-built command
   * line.
   */
  private static class FrozenArgs extends Args {
    private final CommandLine commandLine;
    private final ParamFileInfo paramFileInfo;
    private final ImmutableSet<Artifact> directoryInputs;

    private FrozenArgs(
        CommandLine commandLine,
        ParamFileInfo paramFileInfo,
        ImmutableSet<Artifact> directoryInputs) {
      this.commandLine = commandLine;
      this.paramFileInfo = paramFileInfo;
      this.directoryInputs = directoryInputs;
    }

    @Override
    public ImmutableSet<Artifact> getDirectoryArtifacts() {
      return directoryInputs;
    }

    @Override
    public CommandLine build() {
      return commandLine;
    }

    @Override
    public ParameterFileType getParameterFileType() {
      if (paramFileInfo != null) {
        return paramFileInfo.getFileType();
      } else {
        return ParameterFileType.SHELL_QUOTED;
      }
    }

    @Override
    @Nullable
    public ParamFileInfo getParamFileInfo() {
      return paramFileInfo;
    }

    @Override
    public Mutability mutability() {
      return Mutability.IMMUTABLE;
    }

    @Override
    public CommandLineArgsApi addArgument(
        Object argNameOrValue,
        Object value,
        Object format,
        Object beforeEach,
        Object joinWith,
        Object mapFn,
        Location loc)
        throws EvalException {
      throw new EvalException(null, "cannot modify frozen value");
    }

    @Override
    public CommandLineArgsApi addAll(
        Object argNameOrValue,
        Object values,
        Object mapEach,
        Object formatEach,
        Object beforeEach,
        Boolean omitIfEmpty,
        Boolean uniquify,
        Boolean expandDirectories,
        Object terminateWith,
        Location loc)
        throws EvalException {
      throw new EvalException(null, "cannot modify frozen value");
    }

    @Override
    public CommandLineArgsApi addJoined(
        Object argNameOrValue,
        Object values,
        String joinWith,
        Object mapEach,
        Object formatEach,
        Object formatJoined,
        Boolean omitIfEmpty,
        Boolean uniquify,
        Boolean expandDirectories,
        Location loc)
        throws EvalException {
      throw new EvalException(null, "cannot modify frozen value");
    }

    @Override
    public CommandLineArgsApi useParamsFile(String paramFileArg, Boolean useAlways)
        throws EvalException {
      // TODO(cparsons): Even "frozen" Args may need to use params files.
      // If we go down this path, we will need to rename this class and update the documentation
      // (as this class no longe behaves exactly like a frozen Args object)
      throw new EvalException(null, "cannot modify frozen value");
    }

    @Override
    public CommandLineArgsApi setParamFileFormat(String format) throws EvalException {
      // TODO(cparsons): Even "frozen" Args may need to use params files.
      // If we go down this path, we will need to rename this class and update the documentation
      // (as this class no longe behaves exactly like a frozen Args object)
      throw new EvalException(null, "cannot modify frozen value");
    }
  }

  /** Args module. */
  private static class MutableArgs extends Args {
    private final Mutability mutability;
    private final StarlarkSemantics starlarkSemantics;
    private final SkylarkCustomCommandLine.Builder commandLine;
    private final List<NestedSet<?>> potentialDirectoryArtifacts = new ArrayList<>();
    private final Set<Artifact> directoryArtifacts = new HashSet<>();
    private ParameterFileType parameterFileType = ParameterFileType.SHELL_QUOTED;
    private String flagFormatString;
    private boolean alwaysUseParamFile;

    @Override
    public ParameterFileType getParameterFileType() {
      return parameterFileType;
    }

    @Override
    @Nullable
    public ParamFileInfo getParamFileInfo() {
      if (flagFormatString == null) {
        return null;
      } else {
        return ParamFileInfo.builder(parameterFileType)
            .setFlagFormatString(flagFormatString)
            .setUseAlways(alwaysUseParamFile)
            .setCharset(StandardCharsets.UTF_8)
            .build();
      }
    }

    @Override
    public CommandLineArgsApi addArgument(
        Object argNameOrValue,
        Object value,
        Object format,
        Object beforeEach,
        Object joinWith,
        Object mapFn,
        Location loc)
        throws EvalException {
      if (isImmutable()) {
        throw new EvalException(null, "cannot modify frozen value");
      }
      final String argName;
      if (value == Starlark.UNBOUND) {
        value = argNameOrValue;
        argName = null;
      } else {
        validateArgName(argNameOrValue, loc);
        argName = (String) argNameOrValue;
      }
      if (argName != null) {
        commandLine.add(argName);
      }
      if (value instanceof SkylarkNestedSet || value instanceof SkylarkList) {
        throw new EvalException(
            loc,
            "Args#add doesn't accept vectorized arguments. "
                + "Please use Args#add_all or Args#add_joined.");
      }
      if (mapFn != Runtime.NONE) {
        throw new EvalException(
            loc, "Args#add doesn't accept map_fn. Please eagerly map the value.");
      }
      if (beforeEach != Runtime.NONE) {
        throw new EvalException(null, "'before_each' is not supported for scalar arguments");
      }
      if (joinWith != Runtime.NONE) {
        throw new EvalException(null, "'join_with' is not supported for scalar arguments");
      }
      addScalarArg(
          value,
          format != Runtime.NONE ? (String) format : null,
          mapFn != Runtime.NONE ? (BaseFunction) mapFn : null,
          loc);
      return this;
    }

    @Override
    public CommandLineArgsApi addAll(
        Object argNameOrValue,
        Object values,
        Object mapEach,
        Object formatEach,
        Object beforeEach,
        Boolean omitIfEmpty,
        Boolean uniquify,
        Boolean expandDirectories,
        Object terminateWith,
        Location loc)
        throws EvalException {
      if (isImmutable()) {
        throw new EvalException(null, "cannot modify frozen value");
      }
      final String argName;
      if (values == Starlark.UNBOUND) {
        values = argNameOrValue;
        validateValues(values, loc);
        argName = null;
      } else {
        validateArgName(argNameOrValue, loc);
        argName = (String) argNameOrValue;
      }
      addVectorArg(
          values,
          argName,
          /* mapAll= */ null,
          mapEach != Runtime.NONE ? (BaseFunction) mapEach : null,
          formatEach != Runtime.NONE ? (String) formatEach : null,
          beforeEach != Runtime.NONE ? (String) beforeEach : null,
          /* joinWith= */ null,
          /* formatJoined= */ null,
          omitIfEmpty,
          uniquify,
          expandDirectories,
          terminateWith != Runtime.NONE ? (String) terminateWith : null,
          loc);
      return this;
    }

    @Override
    public CommandLineArgsApi addJoined(
        Object argNameOrValue,
        Object values,
        String joinWith,
        Object mapEach,
        Object formatEach,
        Object formatJoined,
        Boolean omitIfEmpty,
        Boolean uniquify,
        Boolean expandDirectories,
        Location loc)
        throws EvalException {
      if (isImmutable()) {
        throw new EvalException(null, "cannot modify frozen value");
      }
      final String argName;
      if (values == Starlark.UNBOUND) {
        values = argNameOrValue;
        validateValues(values, loc);
        argName = null;
      } else {
        validateArgName(argNameOrValue, loc);
        argName = (String) argNameOrValue;
      }
      addVectorArg(
          values,
          argName,
          /* mapAll= */ null,
          mapEach != Runtime.NONE ? (BaseFunction) mapEach : null,
          formatEach != Runtime.NONE ? (String) formatEach : null,
          /* beforeEach= */ null,
          joinWith,
          formatJoined != Runtime.NONE ? (String) formatJoined : null,
          omitIfEmpty,
          uniquify,
          expandDirectories,
          /* terminateWith= */ null,
          loc);
      return this;
    }

    private void addVectorArg(
        Object value,
        String argName,
        BaseFunction mapAll,
        BaseFunction mapEach,
        String formatEach,
        String beforeEach,
        String joinWith,
        String formatJoined,
        boolean omitIfEmpty,
        boolean uniquify,
        boolean expandDirectories,
        String terminateWith,
        Location loc)
        throws EvalException {
      SkylarkCustomCommandLine.VectorArg.Builder vectorArg;
      if (value instanceof SkylarkNestedSet) {
        SkylarkNestedSet skylarkNestedSet = ((SkylarkNestedSet) value);
        NestedSet<?> nestedSet = skylarkNestedSet.getSet();
        if (expandDirectories) {
          potentialDirectoryArtifacts.add(nestedSet);
        }
        vectorArg = new SkylarkCustomCommandLine.VectorArg.Builder(nestedSet);
      } else {
        @SuppressWarnings("unchecked")
        SkylarkList<Object> skylarkList = (SkylarkList<Object>) value;
        if (expandDirectories) {
          scanForDirectories(skylarkList);
        }
        vectorArg = new SkylarkCustomCommandLine.VectorArg.Builder(skylarkList);
      }
      validateMapEach(mapEach, loc);
      validateFormatString("format_each", formatEach, loc);
      validateFormatString("format_joined", formatJoined, loc);
      vectorArg
          .setLocation(loc)
          .setArgName(argName)
          .setExpandDirectories(expandDirectories)
          .setMapAll(mapAll)
          .setFormatEach(formatEach)
          .setBeforeEach(beforeEach)
          .setJoinWith(joinWith)
          .setFormatJoined(formatJoined)
          .omitIfEmpty(omitIfEmpty)
          .uniquify(uniquify)
          .setTerminateWith(terminateWith)
          .setMapEach(mapEach);
      commandLine.add(vectorArg);
    }

    private void validateArgName(Object argName, Location loc) throws EvalException {
      if (!(argName instanceof String)) {
        throw new EvalException(
            loc,
            String.format(
                "expected value of type 'string' for arg name, got '%s'",
                argName.getClass().getSimpleName()));
      }
    }

    private void validateValues(Object values, Location loc) throws EvalException {
      if (!(values instanceof SkylarkList || values instanceof SkylarkNestedSet)) {
        throw new EvalException(
            loc,
            String.format(
                "expected value of type 'sequence or depset' for values, got '%s'",
                values.getClass().getSimpleName()));
      }
    }

    private void validateMapEach(@Nullable BaseFunction mapEach, Location loc)
        throws EvalException {
      if (mapEach == null) {
        return;
      }
      FunctionSignature sig = mapEach.getSignature();
      boolean valid =
          sig.numMandatoryPositionals() == 1
              && sig.numOptionalPositionals() == 0
              && sig.numMandatoryNamedOnly() == 0
              && sig.numOptionalPositionals() == 0;
      if (!valid) {
        throw new EvalException(
            loc, "map_each must be a function that accepts a single positional argument");
      }
    }

    private void validateFormatString(String argumentName, @Nullable String formatStr, Location loc)
        throws EvalException {
      if (formatStr != null
          && !SingleStringArgFormatter.isValid(formatStr)) {
        throw new EvalException(
            loc,
            String.format(
                "Invalid value for parameter \"%s\": Expected string with a single \"%%s\"",
                argumentName));
      }
    }

    private void addScalarArg(Object value, String format, BaseFunction mapFn, Location loc)
        throws EvalException {
      validateNoDirectory(value, loc);
      validateFormatString("format", format, loc);
      if (format == null && mapFn == null) {
        commandLine.add(value);
      } else {
        ScalarArg.Builder scalarArg =
            new ScalarArg.Builder(value).setLocation(loc).setFormat(format).setMapFn(mapFn);
        commandLine.add(scalarArg);
      }
    }

    private void validateNoDirectory(Object value, Location loc) throws EvalException {
      if (isDirectory(value)) {
        throw new EvalException(
            loc,
            "Cannot add directories to Args#add since they may expand to multiple values. "
                + "Either use Args#add_all (if you want expansion) "
                + "or args.add(directory.path) (if you do not).");
      }
    }

    private static boolean isDirectory(Object object) {
      return ((object instanceof Artifact) && ((Artifact) object).isDirectory());
    }

    @Override
    public CommandLineArgsApi useParamsFile(String paramFileArg, Boolean useAlways)
        throws EvalException {
      if (isImmutable()) {
        throw new EvalException(null, "cannot modify frozen value");
      }
      if (!SingleStringArgFormatter.isValid(paramFileArg)) {
        throw new EvalException(
            null,
            String.format(
                "Invalid value for parameter \"param_file_arg\": "
                    + "Expected string with a single \"%s\"",
                paramFileArg));
      }
      this.flagFormatString = paramFileArg;
      this.alwaysUseParamFile = useAlways;
      return this;
    }

    @Override
    public CommandLineArgsApi setParamFileFormat(String format) throws EvalException {
      if (isImmutable()) {
        throw new EvalException(null, "cannot modify frozen value");
      }
      final ParameterFileType parameterFileType;
      switch (format) {
        case "shell":
          parameterFileType = ParameterFileType.SHELL_QUOTED;
          break;
        case "multiline":
          parameterFileType = ParameterFileType.UNQUOTED;
          break;
        default:
          throw new EvalException(
              null,
              "Invalid value for parameter \"format\": Expected one of \"shell\", \"multiline\"");
      }
      this.parameterFileType = parameterFileType;
      return this;
    }

    private MutableArgs(@Nullable Mutability mutability, StarlarkSemantics starlarkSemantics) {
      this.mutability = mutability != null ? mutability : Mutability.IMMUTABLE;
      this.starlarkSemantics = starlarkSemantics;
      this.commandLine = new SkylarkCustomCommandLine.Builder(starlarkSemantics);
    }

    @Override
    public CommandLine build() {
      return commandLine.build();
    }

    @Override
    public Mutability mutability() {
      return mutability;
    }

    @Override
    public ImmutableSet<Artifact> getDirectoryArtifacts() {
      for (Iterable<?> collection : potentialDirectoryArtifacts) {
        scanForDirectories(collection);
      }
      potentialDirectoryArtifacts.clear();
      return ImmutableSet.copyOf(directoryArtifacts);
    }

    private void scanForDirectories(Iterable<?> objects) {
      for (Object object : objects) {
        if (isDirectory(object)) {
          directoryArtifacts.add((Artifact) object);
        }
      }
    }
  }
}
