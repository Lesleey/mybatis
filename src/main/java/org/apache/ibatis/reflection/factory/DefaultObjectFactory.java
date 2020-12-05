/*
 *    Copyright 2009-2012 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reflection.factory;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.ibatis.reflection.ReflectionException;
import org.apache.ibatis.reflection.ReflectionException;

/**
 * @author Clinton Begin
 */
/**
 * 默认对象工厂，所有对象都要由工厂来产生
 * 
 */
public class DefaultObjectFactory implements ObjectFactory, Serializable {

  private static final long serialVersionUID = -8855120656740914948L;

  @Override
  public <T> T create(Class<T> type) {
    return create(type, null, null);
  }

  /**
   * @param type 需要实例化的类对象
   * @param constructorArgs 实例化类对象的所使用的构造函数的所有参数类型
   * @param constructorArgTypes 实例化类对象所使用的构造参数
   * */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T create(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
    //1.解析接口，获取实际需要实例化的类对象
    Class<?> classToCreate = resolveInterface(type);
    //2. 实例化对象
    return (T) instantiateClass(classToCreate, constructorArgTypes, constructorArgs);
  }

  //默认没有属性可以设置
  @Override
  public void setProperties(Properties properties) {
    // no props for default
  }

  //2.实例化类
  private <T> T instantiateClass(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
    try {
      Constructor<T> constructor;
      //1. 如果没有传入构造参数或者指定一个构造函数，则使用默认的构造函数
      if (constructorArgTypes == null || constructorArgs == null) {
        constructor = type.getDeclaredConstructor();
        if (!constructor.isAccessible()) {
          constructor.setAccessible(true);
        }
        return constructor.newInstance();
      }
      //2. 使用指定的构造函数初始化对象
      constructor = type.getDeclaredConstructor(constructorArgTypes.toArray(new Class[constructorArgTypes.size()]));
      if (!constructor.isAccessible()) {
        constructor.setAccessible(true);
      }
      return constructor.newInstance(constructorArgs.toArray(new Object[constructorArgs.size()]));
    } catch (Exception e) {
      //3. 如果抛出异常，给出友好的提示，包装该异常，打印构造的相关信息，包括初始化类对象的类型、所使用的构造函数和对应的参数等等
      StringBuilder argTypes = new StringBuilder();
      if (constructorArgTypes != null) {
        for (Class<?> argType : constructorArgTypes) {
          argTypes.append(argType.getSimpleName());
          argTypes.append(",");
        }
      }
      StringBuilder argValues = new StringBuilder();
      if (constructorArgs != null) {
        for (Object argValue : constructorArgs) {
          argValues.append(String.valueOf(argValue));
          argValues.append(",");
        }
      }
      throw new ReflectionException("Error instantiating " + type + " with invalid types (" + argTypes + ") or values (" + argValues + "). Cause: " + e, e);
    }
  }

  /**
   * @param type 需要实例化的类对象
   *     如果传入的为接口，则返回一个具体的实现类
   * */
  protected Class<?> resolveInterface(Class<?> type) {
    Class<?> classToCreate;
    if (type == List.class || type == Collection.class || type == Iterable.class) {
      classToCreate = ArrayList.class;

    } else if (type == Map.class) {
      classToCreate = HashMap.class;

    } else if (type == SortedSet.class) { // issue #510 Collections Support
      classToCreate = TreeSet.class;

    } else if (type == Set.class) {
      classToCreate = HashSet.class;

    } else {
      classToCreate = type;
    }
    return classToCreate;
  }

  /**
   * @param type 需要被创建的实例类的类对象
   *    用于判断该类型是否为Collection的子类， 即是否为集合类型
   * */
  @Override
  public <T> boolean isCollection(Class<T> type) {
    return Collection.class.isAssignableFrom(type);
  }

}
