// Copyright 2014 The Bazel Authors. All rights reserved.
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
package com.google.devtools.build.lib.syntax;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.profiler.Profiler;
import com.google.devtools.build.lib.profiler.ProfilerTask;
import com.google.devtools.build.lib.profiler.SilentCloseable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkPrinter;
import com.google.devtools.build.lib.syntax.SkylarkType.SkylarkFunctionType;
import com.google.devtools.build.lib.syntax.StarlarkThread.LexicalFrame;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import javax.annotation.Nullable;

/**
 * A class for Skylark functions provided as builtins by the Skylark implementation. Instances of
 * this class do not need to be serializable because they should effectively be treated as
 * constants.
 */
public class BuiltinFunction extends BaseFunction {

  // Builtins cannot create or modify variable bindings. So it's sufficient to use a shared
  // instance.
  private static final LexicalFrame SHARED_LEXICAL_FRAME_FOR_BUILTIN_FUNCTION_CALLS =
      LexicalFrame.create(Mutability.IMMUTABLE);

  // The underlying invoke() method.
  @Nullable private Method invokeMethod;

  // Classes of extra arguments required beside signature,
  // computed by configure from parameter types of invoke method.
  // TODO(adonovan): eliminate Location, FuncallExpression when they can be derived from the thread.
  private Class<?>[] extraParams; // ordered subset of {Location,FuncallExpression,StarlarkThread}

  // The returnType of the method.
  private Class<?> returnType;

  /** Create unconfigured (signature-less) function from its name. */
  protected BuiltinFunction(String name) {
    super(name);
  }

  /** Creates a BuiltinFunction with the given name and signature. */
  protected BuiltinFunction(String name, FunctionSignature signature) {
    super(name, signature);
    configure();
  }

  @Override
  protected final int getArgArraySize() {
    return invokeMethod.getParameterCount();
  }

  @Override
  @Nullable
  public Object call(Object[] args, @Nullable FuncallExpression ast, StarlarkThread thread)
      throws EvalException, InterruptedException {
    Preconditions.checkNotNull(thread);

    // ast is null when called from Java (as there's no Skylark call site).
    Location loc = ast == null ? Location.BUILTIN : ast.getLocation();

    // Add extra arguments as needed.
    {
      int i = args.length - extraParams.length;
      for (Class<?> cls : extraParams) {
        if (cls == Location.class) {
          args[i] = loc;
        } else if (cls == FuncallExpression.class) {
          args[i] = ast;
        } else if (cls == StarlarkThread.class) {
          args[i] = thread;
        } else {
          throw new IllegalStateException("invalid extra argument: " + cls);
        }
        i++;
      }
    }

    // Last but not least, actually make an inner call to the function with the resolved arguments.
    try (SilentCloseable c =
        Profiler.instance().profile(ProfilerTask.STARLARK_BUILTIN_FN, getName())) {
      thread.enterScope(
          this, SHARED_LEXICAL_FRAME_FOR_BUILTIN_FUNCTION_CALLS, ast, thread.getGlobals());
      return invokeMethod.invoke(this, args);
    } catch (InvocationTargetException x) {
      Throwable e = x.getCause();

      if (e instanceof EvalException) {
        throw ((EvalException) e).ensureLocation(loc);
      } else if (e instanceof IllegalArgumentException) {
        throw new EvalException(loc, "illegal argument in call to " + getName(), e);
      }
      Throwables.throwIfInstanceOf(e, InterruptedException.class);
      Throwables.throwIfUnchecked(e);
      throw badCallException(loc, e, args);
    } catch (IllegalArgumentException e) {
      // Either this was thrown by Java itself, or it's a bug
      // To cover the first case, let's manually check the arguments.
      final int len = args.length - extraParams.length;
      final Class<?>[] types = invokeMethod.getParameterTypes();
      for (int i = 0; i < args.length; i++) {
        if (args[i] != null && !types[i].isAssignableFrom(args[i].getClass())) {
          String paramName =
              i < len ? signature.getParameterNames().get(i) : extraParams[i - len].getName();
          throw new EvalException(
              loc,
              String.format(
                  "argument '%s' has type '%s', but should be '%s'\nin call to builtin %s %s",
                  paramName,
                  EvalUtils.getDataTypeName(args[i]),
                  EvalUtils.getDataTypeNameFromClass(types[i]),
                  hasSelfArgument() ? "method" : "function",
                  getShortSignature()));
        }
      }
      throw badCallException(loc, e, args);
    } catch (IllegalAccessException e) {
      throw badCallException(loc, e, args);
    } finally {
      thread.exitScope();
    }
  }

  private static String stacktraceToString(StackTraceElement[] elts) {
    StringBuilder b = new StringBuilder();
    for (StackTraceElement e : elts) {
      b.append(e);
      b.append("\n");
    }
    return b.toString();
  }

  private IllegalStateException badCallException(Location loc, Throwable e, Object... args) {
    // If this happens, it's a bug in our code.
    return new IllegalStateException(
        String.format(
            "%s%s (%s)\n"
                + "while calling %s with args %s\n"
                + "Java parameter types: %s\nStarlark type checks: %s",
            (loc == null) ? "" : loc + ": ",
            Arrays.asList(args),
            e.getClass().getName(),
            stacktraceToString(e.getStackTrace()),
            this,
            Arrays.asList(invokeMethod.getParameterTypes()),
            getEnforcedArgumentTypes()),
        e);
  }

  /**
   * Configure the reflection mechanism. Called directly by constructor for BuiltinFunctions created
   * with a signature, and called after annotation processing for other BuiltinFunctions.
   */
  @Override
  final void configure() {
    this.invokeMethod = findMethod(this.getClass(), "invoke");
    Class<?>[] parameterTypes = invokeMethod.getParameterTypes();
    int numParameters = signature.numParameters();
    this.extraParams = extraParams(numParameters, parameterTypes);

    if (enforcedArgumentTypes != null) {
      for (int i = 0; i < numParameters; i++) {
        SkylarkType enforcedType = enforcedArgumentTypes.get(i);
        if (enforcedType != null) {
          Class<?> parameterType = parameterTypes[i];
          String msg =
              String.format(
                  "fun %s(%s), param %s, enforcedType: %s (%s); parameterType: %s",
                  getName(),
                  signature,
                  signature.getParameterNames().get(i),
                  enforcedType,
                  enforcedType.getType(),
                  parameterType);
          if (enforcedType instanceof SkylarkType.Simple
              || enforcedType instanceof SkylarkFunctionType) {
            Preconditions.checkArgument(
                parameterType.isAssignableFrom(enforcedType.getType()), msg);

            // No need to enforce Simple types on the Skylark side, the JVM will do it for us.
            enforcedArgumentTypes.set(i, null);
          } else if (enforcedType instanceof SkylarkType.Combination) {
            Preconditions.checkArgument(enforcedType.getType() == parameterType, msg);
          } else {
            Preconditions.checkArgument(
                parameterType == Object.class || parameterType == null, msg);
          }
        }
      }
      // No need for the enforcedArgumentTypes List if all the types were Simple
      enforcedArgumentTypes = valueListOrNull(enforcedArgumentTypes);
    }

    if (returnType != null) {
      Class<?> type = returnType;
      Class<?> methodReturnType = invokeMethod.getReturnType();
      Preconditions.checkArgument(
          type == methodReturnType,
          "signature for function %s says it returns %s but its invoke method returns %s",
          getName(),
          returnType,
          methodReturnType);
    }
  }

  // Returns the list of extra parameters beyond those in the signature.
  private Class<?>[] extraParams(int i, Class<?>[] parameterTypes) {
    List<Class<?>> extra = Lists.newArrayList();
    for (Class<?> cls : EXTRA_PARAM_CLASSES) {
      if (i < parameterTypes.length && parameterTypes[i] == cls) {
        extra.add(cls);
        i++;
      }
    }
    if (i != parameterTypes.length) {
      throw new IllegalStateException(
          String.format(
              "bad argument count for %s: method has %s arguments, type list has %s",
              getName(), i, parameterTypes.length));
    }
    return extra.toArray(new Class<?>[0]);
  }

  private static final Class<?>[] EXTRA_PARAM_CLASSES = {
    Location.class, FuncallExpression.class, StarlarkThread.class
  };

  /** Returns list, or null if all its elements are null. */
  @Nullable
  private static <E> List<E> valueListOrNull(List<E> list) {
    if (list != null) {
      for (E value : list) {
        if (value != null) {
          return list;
        }
      }
    }
    return null;
  }

  // finds the method and makes it accessible (which is needed to find it, and later to use it)
  private static Method findMethod(Class<?> cls, String name) {
    Method found = null;
    for (Method method : cls.getDeclaredMethods()) {
      method.setAccessible(true);
      if (name.equals(method.getName())) {
        if (found != null) {
          throw new IllegalArgumentException(
              String.format("class %s has more than one method named %s", cls.getName(), name));
        }
        found = method;
      }
    }
    if (found == null) {
      throw new NoSuchElementException(
          String.format("class %s doesn't have a method named %s", cls.getName(), name));
    }
    return found;
  }

  @Override
  public void repr(SkylarkPrinter printer) {
    printer.append("<built-in function " + getName() + ">");
  }
}
