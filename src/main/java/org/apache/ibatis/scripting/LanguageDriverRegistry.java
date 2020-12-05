/*
 * Copyright 2012 MyBatis.org.
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
package org.apache.ibatis.scripting;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Frank D. Martinez [mnesarco]
 */
/**
 * 脚本语言注册器
 *
 */
public class LanguageDriverRegistry {

  /**
   *  key: 语言驱动类的类对象， value: 语言驱动类实例
   * */
  private final Map<Class<?>, LanguageDriver> LANGUAGE_DRIVER_MAP = new HashMap<Class<?>, LanguageDriver>();

  private Class<?> defaultDriverClass = null;

  /**
   * @param cls 向语言驱动注册器注册的语言驱动的类对象
   * */
  public void register(Class<?> cls) {
    //1. 验证参数的有效性
    if (cls == null) {
      throw new IllegalArgumentException("null is not a valid Language Driver");
    }
    if (!LanguageDriver.class.isAssignableFrom(cls)) {
      throw new ScriptingException(cls.getName() + " does not implements " + LanguageDriver.class.getName());
    }
    //2. 没有注册过才会进行注册
    LanguageDriver driver = LANGUAGE_DRIVER_MAP.get(cls);
    if (driver == null) {
      try {
        driver = (LanguageDriver) cls.newInstance();
        LANGUAGE_DRIVER_MAP.put(cls, driver);
      } catch (Exception ex) {
        throw new ScriptingException("Failed to load language driver for " + cls.getName(), ex);
      }
    }
  }

  public LanguageDriver getDriver(Class<?> cls) {
    return LANGUAGE_DRIVER_MAP.get(cls);
  }

  public LanguageDriver getDefaultDriver() {
    return getDriver(getDefaultDriverClass());
  }

  public Class<?> getDefaultDriverClass() {
    return defaultDriverClass;
  }

  public void setDefaultDriverClass(Class<?> defaultDriverClass) {
    register(defaultDriverClass);
    this.defaultDriverClass = defaultDriverClass;
  }

}
