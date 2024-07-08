/*
 *    Copyright 2009-2023 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reflection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.invoker.AmbiguousMethodInvoker;
import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;
import org.apache.ibatis.util.MapUtil;

/**
 * This class represents a cached set of class definition information that allows for easy mapping between property
 * names and getter/setter methods.
 * 反射器，用于解析和存储目标类中的元信息：
 * Reflector 这个类的用途主要是是通过反射获取目标类的 getter 方法及其返回值类型，setter 方法及其参数值类型等元信息。
 * 并将获取到的元信息缓存到相应的集合中，供后续使用
 *
 * @author Clinton Begin
 */
public class Reflector {

  private static final MethodHandle isRecordMethodHandle = getIsRecordMethodHandle();
  private final Class<?> type;
  /**
   * 可读属性名称数组，用于保存 getter 方法对应的属性名称
   */
  private final String[] readablePropertyNames;
  /**
   * 可写属性名称数组，用于保存 setter 方法对应的属性名称
   */
  private final String[] writablePropertyNames;
  /**
   * 用于保存属性名称到 Invoke 的映射。setter 方法会被封装到 MethodInvoker 对象中
   */
  private final Map<String, Invoker> setMethods = new HashMap<>();
  /**
   * 用于保存属性名称到 Invoke 的映射。同上，getter 方法也会被封装到 MethodInvoker 对象中
   */
  private final Map<String, Invoker> getMethods = new HashMap<>();
  /**
   * 用于保存 setter 对应的属性名与参数类型的映射
   */
  private final Map<String, Class<?>> setTypes = new HashMap<>();
  /**
   * 用于保存 getter 对应的属性名与返回值类型的映射
   */
  private final Map<String, Class<?>> getTypes = new HashMap<>();
  private Constructor<?> defaultConstructor;

  /**
   * 用于保存大写属性名与属性名之间的映射，比如 <NAME, name>
   */
  private final Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

  /**
   * Reflector类的构造函数。
   * 根据给定的类，初始化反射器，包括收集和处理类的构造函数、方法和字段。
   * 对于记录类，只处理获取方法；对于普通类，处理获取、设置方法和字段。
   * 建立属性名的大小写不敏感映射，以便后续操作可以忽略属性名的大小写。
   *
   * @param clazz 要反射的类
   */
  public Reflector(Class<?> clazz) {
    type = clazz;
    // 添加默认构造函数
    addDefaultConstructor(clazz);
    Method[] classMethods = getClassMethods(clazz);
    // 根据类型是否为记录类，选择性地添加获取方法
    if (isRecord(type)) {
      addRecordGetMethods(classMethods);
    } else {
      // 解析 getter 方法，并将解析结果放入 getMethods 中
      addGetMethods(classMethods);
      // 解析 setter 方法，并将解析结果放入 setMethods 中
      addSetMethods(classMethods);
      // 解析属性字段，并将解析结果添加到 setMethods 或 getMethods 中
      addFields(clazz);
    }
    // 从 getMethods 映射中获取可读属性名数组
    readablePropertyNames = getMethods.keySet().toArray(new String[0]);
    // 从 setMethods 映射中获取可写属性名数组
    writablePropertyNames = setMethods.keySet().toArray(new String[0]);
    // 将所有属性名的大写形式作为键，属性名作为值，
    // 存入到 caseInsensitivePropertyMap 中
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  private void addRecordGetMethods(Method[] methods) {
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 0)
        .forEach(m -> addGetMethod(m.getName(), m, false));
  }

  private void addDefaultConstructor(Class<?> clazz) {
    Constructor<?>[] constructors = clazz.getDeclaredConstructors();
    Arrays.stream(constructors).filter(constructor -> constructor.getParameterTypes().length == 0).findAny()
        .ifPresent(constructor -> this.defaultConstructor = constructor);
  }

  /**
   * 添加getter方法并解决可能的命名冲突。
   * 通过分析方法名和参数，识别出所有无参数的getter方法，并将它们映射到对应的属性名。
   * 如果存在同名的getter方法（即属性名冲突），则记录这些冲突并随后解决它们。
   *
   * @param methods 类中定义的所有方法。
   */
  private void addGetMethods(Method[] methods) {
    // 使用HashMap来存储可能冲突的getter方法，键为属性名，值为该属性名对应的getter方法列表。
    Map<String, List<Method>> conflictingGetters = new HashMap<>();

    // 流式处理methods数组，筛选出无参数且被识别为getter方法的方法。
    Arrays.stream(methods)
        // getter 方法不应该有参数，若存在参数，则忽略当前方法;以 get或 is开头的方法
        .filter(m -> m.getParameterTypes().length == 0 && PropertyNamer.isGetter(m.getName()))
        .forEach(m -> {
          // 将getter方法映射到对应的属性名，并处理命名冲突。
          addMethodConflict(conflictingGetters, PropertyNamer.methodToProperty(m.getName()), m);
        });

    // 解决getter方法的命名冲突。
    resolveGetterConflicts(conflictingGetters);
  }


  /**
   * 解决具有冲突的getter方法。
   * 当存在多个同名的getter方法时，需要确定一个“胜利者”（winner），或者标记为模糊（ambiguous）。
   * 决定胜利者的规则包括：返回类型优先级、布尔方法优先。
   *
   * @param conflictingGetters 包含冲突getter方法的映射，键为属性名，值为getter方法列表。
   */
  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    // 遍历每个属性的getter方法列表
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      Method winner = null;
      String propName = entry.getKey();
      boolean isAmbiguous = false;
      // 遍历每个getter方法，决定胜利者
      for (Method candidate : entry.getValue()) {
        // 如果还没有胜利者，则当前方法自动成为胜利者
        if (winner == null) {
          winner = candidate;
          continue;
        }
        Class<?> winnerType = winner.getReturnType();
        Class<?> candidateType = candidate.getReturnType();
        // 如果返回类型相同，且不是布尔类型，则标记为模糊并退出循环
        if (candidateType.equals(winnerType)) {
          if (!boolean.class.equals(candidateType)) {
            isAmbiguous = true;
            break;
          }
          // 如果是布尔类型的方法，优先选择以is开头的方法作为胜利者
          if (candidate.getName().startsWith("is")) {
            winner = candidate;
          }
        } else if (candidateType.isAssignableFrom(winnerType)) {
          // winnerType是 candidateType 的子类，类型上更为具体，则认为当前的 winner 仍是合适的，无需做什么事情
          // OK getter type is descendant
        } else if (winnerType.isAssignableFrom(candidateType)) {
          // candidateType 是 winnerType 的子类，此时认为 candidate 方法，更为合适，故将 winner 更新为 candidate
          winner = candidate;
        } else {
          // 如果返回类型既不是继承关系，也不是相同类型，则标记为模糊并退出循环
          isAmbiguous = true;
          break;
        }
      }
      // 将筛选出的方法添加到 getMethods 中，并将方法返回值添加到 getTypes 中
      addGetMethod(propName, winner, isAmbiguous);
    }
  }


  /**
   * 添加获取方法的调用者。
   *
   * 此方法用于根据给定的方法和属性名，添加一个用于调用该方法的调用者对象到存储中。
   * 如果方法是重载的并且类型不明确，将使用特殊的调用者对象来处理这种情况。
   *
   * @param name 属性名，对应于获取方法的名称。
   * @param method 获取方法的Method对象。
   * @param isAmbiguous 指示方法是否为重载且类型不明确的方法。
   */
  private void addGetMethod(String name, Method method, boolean isAmbiguous) {
    // 根据方法是否是重载且类型不明确的，选择合适的MethodInvoker类型。
    MethodInvoker invoker = isAmbiguous ? new AmbiguousMethodInvoker(method, MessageFormat.format(
        "Illegal overloaded getter method with ambiguous type for property ''{0}'' in class ''{1}''. This breaks the JavaBeans specification and can cause unpredictable results.",
        name, method.getDeclaringClass().getName())) : new MethodInvoker(method);
    // 将调用者对象添加到存储中，以供后续使用。
    getMethods.put(name, invoker);
    // 解析方法的返回类型，并将其转换为Class对象。
    Type returnType = TypeParameterResolver.resolveReturnType(method, type);
    // 将解析后的返回类型存储起来。
    getTypes.put(name, typeToClass(returnType));
  }


  private void addSetMethods(Method[] methods) {
    Map<String, List<Method>> conflictingSetters = new HashMap<>();
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 1 && PropertyNamer.isSetter(m.getName()))
        .forEach(m -> addMethodConflict(conflictingSetters, PropertyNamer.methodToProperty(m.getName()), m));
    resolveSetterConflicts(conflictingSetters);
  }

  /**
   * 添加方法冲突到映射中。
   * 如果属性名有效，则将方法添加到相应属性名的方法列表中。如果属性名对应的列表不存在，
   * 则先创建一个空列表，然后将方法添加到该列表中。
   * 这个方法用于收集和组织具有相同属性名（可能引起冲突）的方法。
   *
   * @param conflictingMethods 存储方法冲突的映射，键为属性名，值为该属性名下冲突的方法列表。
   * @param name 属性名，用于映射的关键字。
   * @param method 要添加到冲突列表中的方法。
   */
  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
    // 检查属性名是否有效，只有有效的属性名才会被添加到映射中
    if (isValidPropertyName(name)) {
      // 使用计算如果Absent的策略，为属性名在映射中创建一个方法列表，如果已存在则直接获取
      List<Method> list = MapUtil.computeIfAbsent(conflictingMethods, name, k -> new ArrayList<>());
      // 将方法添加到属性名对应的方法列表中
      list.add(method);
    }
  }

  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    for (Entry<String, List<Method>> entry : conflictingSetters.entrySet()) {
      String propName = entry.getKey();
      List<Method> setters = entry.getValue();
      Class<?> getterType = getTypes.get(propName);
      boolean isGetterAmbiguous = getMethods.get(propName) instanceof AmbiguousMethodInvoker;
      boolean isSetterAmbiguous = false;
      Method match = null;
      for (Method setter : setters) {
        if (!isGetterAmbiguous && setter.getParameterTypes()[0].equals(getterType)) {
          // should be the best match
          match = setter;
          break;
        }
        if (!isSetterAmbiguous) {
          match = pickBetterSetter(match, setter, propName);
          isSetterAmbiguous = match == null;
        }
      }
      if (match != null) {
        addSetMethod(propName, match);
      }
    }
  }

  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    if (setter1 == null) {
      return setter2;
    }
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    if (paramType1.isAssignableFrom(paramType2)) {
      return setter2;
    }
    if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }
    MethodInvoker invoker = new AmbiguousMethodInvoker(setter1,
        MessageFormat.format(
            "Ambiguous setters defined for property ''{0}'' in class ''{1}'' with types ''{2}'' and ''{3}''.", property,
            setter2.getDeclaringClass().getName(), paramType1.getName(), paramType2.getName()));
    setMethods.put(property, invoker);
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(setter1, type);
    setTypes.put(property, typeToClass(paramTypes[0]));
    return null;
  }

  private void addSetMethod(String name, Method method) {
    MethodInvoker invoker = new MethodInvoker(method);
    setMethods.put(name, invoker);
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
    setTypes.put(name, typeToClass(paramTypes[0]));
  }

  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    if (src instanceof Class) {
      result = (Class<?>) src;
    } else if (src instanceof ParameterizedType) {
      result = (Class<?>) ((ParameterizedType) src).getRawType();
    } else if (src instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      if (componentType instanceof Class) {
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        Class<?> componentClass = typeToClass(componentType);
        result = Array.newInstance(componentClass, 0).getClass();
      }
    }
    if (result == null) {
      result = Object.class;
    }
    return result;
  }

  private void addFields(Class<?> clazz) {
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      if (!setMethods.containsKey(field.getName())) {
        // issue #379 - removed the check for final because JDK 1.5 allows
        // modification of final fields through reflection (JSR-133). (JGB)
        // pr #16 - final static can only be set by the classloader
        int modifiers = field.getModifiers();
        if ((!Modifier.isFinal(modifiers) || !Modifier.isStatic(modifiers))) {
          addSetField(field);
        }
      }
      if (!getMethods.containsKey(field.getName())) {
        addGetField(field);
      }
    }
    if (clazz.getSuperclass() != null) {
      addFields(clazz.getSuperclass());
    }
  }

  private void addSetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private void addGetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private boolean isValidPropertyName(String name) {
    return (!name.startsWith("$") && !"serialVersionUID".equals(name) && !"class".equals(name));
  }

  /**
   * This method returns an array containing all methods declared in this class and any superclass. We use this method,
   * instead of the simpler <code>Class.getMethods()</code>, because we want to look for private methods as well.
   *
   * @param clazz
   *          The class
   *
   * @return An array containing all methods in this class
   */
  private Method[] getClassMethods(Class<?> clazz) {
    Map<String, Method> uniqueMethods = new HashMap<>();
    Class<?> currentClass = clazz;
    while (currentClass != null && currentClass != Object.class) {
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      // we also need to look for interface methods -
      // because the class may be abstract
      Class<?>[] interfaces = currentClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }

      currentClass = currentClass.getSuperclass();
    }

    Collection<Method> methods = uniqueMethods.values();

    return methods.toArray(new Method[0]);
  }

  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) {
      if (!currentMethod.isBridge()) {
        String signature = getSignature(currentMethod);
        // check to see if the method is already known
        // if it is known, then an extended class must have
        // overridden a method
        if (!uniqueMethods.containsKey(signature)) {
          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }

  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    Class<?> returnType = method.getReturnType();
    sb.append(returnType.getName()).append('#');
    sb.append(method.getName());
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      sb.append(i == 0 ? ':' : ',').append(parameters[i].getName());
    }
    return sb.toString();
  }

  /**
   * Checks whether can control member accessible.
   *
   * @return If can control member accessible, it return {@literal true}
   *
   * @since 3.5.0
   */
  public static boolean canControlMemberAccessible() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /**
   * Gets the name of the class the instance provides information for.
   *
   * @return The class name
   */
  public Class<?> getType() {
    return type;
  }

  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    }
    throw new ReflectionException("There is no default constructor for " + type);
  }

  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /**
   * Gets the type for a property setter.
   *
   * @param propertyName
   *          - the name of the property
   *
   * @return The Class of the property setter
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets the type for a property getter.
   *
   * @param propertyName
   *          - the name of the property
   *
   * @return The Class of the property getter
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets an array of the readable properties for an object.
   *
   * @return The array
   */
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /**
   * Gets an array of the writable properties for an object.
   *
   * @return The array
   */
  public String[] getSetablePropertyNames() {
    return writablePropertyNames;
  }

  /**
   * Check to see if a class has a writable property by name.
   *
   * @param propertyName
   *          - the name of the property to check
   *
   * @return True if the object has a writable property by the name
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.containsKey(propertyName);
  }

  /**
   * Check to see if a class has a readable property by name.
   *
   * @param propertyName
   *          - the name of the property to check
   *
   * @return True if the object has a readable property by the name
   */
  public boolean hasGetter(String propertyName) {
    return getMethods.containsKey(propertyName);
  }

  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }

  /**
   * Class.isRecord() alternative for Java 15 and older.
   */
  private static boolean isRecord(Class<?> clazz) {
    try {
      return isRecordMethodHandle != null && (boolean) isRecordMethodHandle.invokeExact(clazz);
    } catch (Throwable e) {
      throw new ReflectionException("Failed to invoke 'Class.isRecord()'.", e);
    }
  }

  private static MethodHandle getIsRecordMethodHandle() {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    MethodType mt = MethodType.methodType(boolean.class);
    try {
      return lookup.findVirtual(Class.class, "isRecord", mt);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      return null;
    }
  }
}
