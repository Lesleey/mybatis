/*
 *    Copyright 2009-2011 the original author or authors.
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
package org.apache.ibatis.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * @author Clinton Begin
 */
/**
 * 元类： 类似于外观模式， 提供了很多简便的方法，实际上内部通过 reflector完成
 * 
 */
public class MetaClass {

  private Reflector reflector;

  private MetaClass(Class<?> type) {
    this.reflector = Reflector.forClass(type);
  }

  public static MetaClass forClass(Class<?> type) {
    return new MetaClass(type);
  }

  public static boolean isClassCacheEnabled() {
    return Reflector.isClassCacheEnabled();
  }

  public static void setClassCacheEnabled(boolean classCacheEnabled) {
    Reflector.setClassCacheEnabled(classCacheEnabled);
  }

  /**
   * 获取指定字段的类型对应的元类
   * */
  public MetaClass metaClassForProperty(String name) {
    //1. 获取指定字段的get方法的返回值
    Class<?> propType = reflector.getGetterType(name);
    //2. 获取返回值类型对应的元类
    return MetaClass.forClass(propType);
  }

  /**
   * @param name Ognl表达式
   *   根据给定的 Ognl表达式 获取有效的 Ognl表达式
   * */
  public String findProperty(String name) {
    StringBuilder prop = buildProperty(name, new StringBuilder());
    return prop.length() > 0 ? prop.toString() : null;
  }

  public String findProperty(String name, boolean useCamelCaseMapping) {
    if (useCamelCaseMapping) {
      name = name.replace("_", "");
    }
    return findProperty(name);
  }

  // 获取可读字段名称
  public String[] getGetterNames() {
    return reflector.getGetablePropertyNames();
  }

  //获取可写字段名称
  public String[] getSetterNames() {
    return reflector.getSetablePropertyNames();
  }

  // 根据给定的 ognl表达式，获取对应的属性的set方法的参数类型
  public Class<?> getSetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop.getName());
      return metaProp.getSetterType(prop.getChildren());
    } else {
      return reflector.getSetterType(prop.getName());
    }
  }

  // 根据给定的 ognl表达式，获取对应的属性的get方法的返回值类型
  public Class<?> getGetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop);
      return metaProp.getGetterType(prop.getChildren());
    }
    // issue #506. Resolve the type inside a Collection Object
    return getGetterType(prop);
  }

  // 根据给定的 ognl 表达式，获取属性对应的get方法的返回值类型构建的元类
  private MetaClass metaClassForProperty(PropertyTokenizer prop) {
    Class<?> propType = getGetterType(prop);
    return MetaClass.forClass(propType);
  }

  /**
   * 根据 ognl 表达式构建的分词器，获取 name 对应的属性的 get 方法的返回值
   *   用于处理字段为集合的情况
   */
  private Class<?> getGetterType(PropertyTokenizer prop) {
    Class<?> type = reflector.getGetterType(prop.getName());
    if (prop.getIndex() != null && Collection.class.isAssignableFrom(type)) {
      //1. 获取分词器中 name 属性当前类对应的类型
      Type returnType = getGenericGetterType(prop.getName());
      //2. 如果返回值类型为参数化类型含有泛型）
      if (returnType instanceof ParameterizedType) {
        //2.1 获取该返回值类型的实际参数类型
        Type[] actualTypeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
        //2.2 如果实际类型只包含一个（例如List<String>）
        if (actualTypeArguments != null && actualTypeArguments.length == 1) {
          //2.2.1 则返回值类型为实际参数类型
          returnType = actualTypeArguments[0];
          //2.2.2 如果实际参数类型为 class，则进行强转
          if (returnType instanceof Class) {
            type = (Class<?>) returnType;
          //2.2.3 如果实际参数类型为参数化类型，则返回声明该参数化类型的类的类对象（比如List<String>中的List）
          } else if (returnType instanceof ParameterizedType) {
            type = (Class<?>) ((ParameterizedType) returnType).getRawType();
          }
        }
      }
    }
    return type;
  }

  /**
   *  根据属性名称获取对应的类型
   * */
  private Type getGenericGetterType(String propertyName) {
    try {
      Invoker invoker = reflector.getGetInvoker(propertyName);
      //1. 如果该属性有对应的get方法，则为返回值类型
      if (invoker instanceof MethodInvoker) {
        Field _method = MethodInvoker.class.getDeclaredField("method");
        _method.setAccessible(true);
        Method method = (Method) _method.get(invoker);
        return method.getGenericReturnType();
      //2. 如果有这个字段，则获取字段类型
      } else if (invoker instanceof GetFieldInvoker) {
        Field _field = GetFieldInvoker.class.getDeclaredField("field");
        _field.setAccessible(true);
        Field field = (Field) _field.get(invoker);
        return field.getGenericType();
      }
    } catch (NoSuchFieldException e) {
    } catch (IllegalAccessException e) {
    }
    return null;
  }

  //根据ognl表达式，判断对应的属性有没有set调用者
  public boolean hasSetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (reflector.hasSetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop.getName());
        return metaProp.hasSetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasSetter(prop.getName());
    }
  }

  //根据ognl表达式，判断对应的属性有没有get调用者
  public boolean hasGetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (reflector.hasGetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop);
        return metaProp.hasGetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasGetter(prop.getName());
    }
  }

  // 根据字段名称获取对应的 get / set 调用者
  public Invoker getGetInvoker(String name) {
    return reflector.getGetInvoker(name);
  }

  public Invoker getSetInvoker(String name) {
    return reflector.getSetInvoker(name);
  }

  /**
   * @param name Ognl表达式
   * @param builder  正在构建的ognl表达式
   *     根据给定的 ognl表达式，返回有效的Ognl表达式
   * */
  private StringBuilder buildProperty(String name, StringBuilder builder) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    //1. 如果 表达式 包含 "."， 则先拼接分词器中 name 对应的字段名称，在递归进行构建
    if (prop.hasNext()) {
      String propertyName = reflector.findPropertyName(prop.getName());
      if (propertyName != null) {
        builder.append(propertyName);
        builder.append(".");
        MetaClass metaProp = metaClassForProperty(propertyName);
        metaProp.buildProperty(prop.getChildren(), builder);
      }
    //2. 否则, 则表示 name 就为该类的一个属性，则直接拼接字段名称
    } else {
      String propertyName = reflector.findPropertyName(name);
      if (propertyName != null) {
        builder.append(propertyName);
      }
    }
    return builder;
  }

  //该类是否有默认的构造器
  public boolean hasDefaultConstructor() {
    return reflector.hasDefaultConstructor();
  }

}
