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

/**
 * ReflectorFactory接口用于创建和管理Reflector实例。
 * Reflector实例用于反射机制中对类的操作，提供了一种静态方法的访问方式。
 */
public interface ReflectorFactory {

  /**
   * 检查类缓存是否启用。
   * 类缓存用于存储已经反射过的类的信息，以提高后续访问同一类的效率。
   *
   * @return 如果类缓存启用，则返回true；否则返回false。
   */
  boolean isClassCacheEnabled();

  /**
   * 设置类缓存的启用状态。
   * 通过启用或禁用类缓存，可以控制Reflector在反射操作时是否使用缓存的信息。
   * 这对于在性能和内存使用之间进行权衡很有用。
   *
   * @param classCacheEnabled 如果为true，则启用类缓存；如果为false，则禁用类缓存。
   */
  void setClassCacheEnabled(boolean classCacheEnabled);

  /**
   * 根据类类型查找并返回对应的Reflector实例。
   * 通过Reflector实例，可以对指定类进行方法的反射调用等操作。
   *
   * @param type 需要反射的类的Class对象。
   * @return 对应于指定类的Reflector实例。
   */
  Reflector findForClass(Class<?> type);
}

