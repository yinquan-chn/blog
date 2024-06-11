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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.ibatis.util.MapUtil;

/**
 * DefaultReflectorFactory 用于创建 Reflector，同时兼有缓存的功能
 */
public class DefaultReflectorFactory implements ReflectorFactory {
  /**
   * 是否开启缓存
   */
  private boolean classCacheEnabled = true;
  /**
   * 缓存 Reflector
   */
  private final ConcurrentMap<Class<?>, Reflector> reflectorMap = new ConcurrentHashMap<>();

  public DefaultReflectorFactory() {
  }

  @Override
  public boolean isClassCacheEnabled() {
    return classCacheEnabled;
  }

  @Override
  public void setClassCacheEnabled(boolean classCacheEnabled) {
    this.classCacheEnabled = classCacheEnabled;
  }

  @Override
  public Reflector findForClass(Class<?> type) {
    // 是否开启缓存
    if (classCacheEnabled) {
      // synchronized (type) removed see issue #461
      // 从缓存中获取 Reflector
      return MapUtil.computeIfAbsent(reflectorMap, type, Reflector::new);
    }
    // 直接创建 Reflector
    return new Reflector(type);
  }

}
