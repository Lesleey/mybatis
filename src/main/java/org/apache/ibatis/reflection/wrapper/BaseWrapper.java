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
package org.apache.ibatis.reflection.wrapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectionException;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * @author Clinton Begin
 */
/**
 * 对象包装器的基类，模板方法模式：用以提供一些公共的方法
 * 
 */
public abstract class BaseWrapper implements ObjectWrapper {

  protected static final Object[] NO_ARGUMENTS = new Object[0];
  protected MetaObject metaObject;

  protected BaseWrapper(MetaObject metaObject) {
    this.metaObject = metaObject;
  }

  /**
   * @param prop  表达式对应的分词器
   * @param object 对象
   *    根据解析表达式获取到的分词器获取对象中的属性的值, 只针对对集合的操作
   *      解析 分词器中 name 对应的值
   * */
  protected Object resolveCollection(PropertyTokenizer prop, Object object) {
    //1. 如果 name 为空，则表示该对象就为集合，例如表达式为 [0],则直接返回该对象,
    if ("".equals(prop.getName())) {
      return object;
    //2. 否则，则可能集合为该对象的一个属性，例如表达式为 child[0]
    } else {
      return metaObject.getValue(prop.getName());
    }
  }

  /**
   *  根据分词器获取实际（indexName）的值
   *    解析 分词器中  index 对应的值
   * */
  protected Object getCollectionValue(PropertyTokenizer prop, Object collection) {
    //1. 如果集合为 map， 则 index 为 key
    if (collection instanceof Map) {
      return ((Map) collection).get(prop.getIndex());
    //2. 如果为其他的集合类型，则 index 为索引
    } else {
      int i = Integer.parseInt(prop.getIndex());
      if (collection instanceof List) {
          //list[0]
        return ((List) collection).get(i);
      } else if (collection instanceof Object[]) {
        return ((Object[]) collection)[i];
      } else if (collection instanceof char[]) {
        return ((char[]) collection)[i];
      } else if (collection instanceof boolean[]) {
        return ((boolean[]) collection)[i];
      } else if (collection instanceof byte[]) {
        return ((byte[]) collection)[i];
      } else if (collection instanceof double[]) {
        return ((double[]) collection)[i];
      } else if (collection instanceof float[]) {
        return ((float[]) collection)[i];
      } else if (collection instanceof int[]) {
        return ((int[]) collection)[i];
      } else if (collection instanceof long[]) {
        return ((long[]) collection)[i];
      } else if (collection instanceof short[]) {
        return ((short[]) collection)[i];
      } else {
        throw new ReflectionException("The '" + prop.getName() + "' property of " + collection + " is not a List or Array.");
      }
    }
  }

  /**
   * @param prop 表达式对应的分词器
   * @param collection 具体的对象
   * @param value
   *   通过表达式给对象赋值
   * */
  protected void setCollectionValue(PropertyTokenizer prop, Object collection, Object value) {
    //1. 如果集合类型为 map，则 index 为 key
    if (collection instanceof Map) {
      ((Map) collection).put(prop.getIndex(), value);
    //2. 否则， index 为索引
    } else {
      int i = Integer.parseInt(prop.getIndex());
      if (collection instanceof List) {
        ((List) collection).set(i, value);
      } else if (collection instanceof Object[]) {
        ((Object[]) collection)[i] = value;
      } else if (collection instanceof char[]) {
        ((char[]) collection)[i] = (Character) value;
      } else if (collection instanceof boolean[]) {
        ((boolean[]) collection)[i] = (Boolean) value;
      } else if (collection instanceof byte[]) {
        ((byte[]) collection)[i] = (Byte) value;
      } else if (collection instanceof double[]) {
        ((double[]) collection)[i] = (Double) value;
      } else if (collection instanceof float[]) {
        ((float[]) collection)[i] = (Float) value;
      } else if (collection instanceof int[]) {
        ((int[]) collection)[i] = (Integer) value;
      } else if (collection instanceof long[]) {
        ((long[]) collection)[i] = (Long) value;
      } else if (collection instanceof short[]) {
        ((short[]) collection)[i] = (Short) value;
      } else {
        throw new ReflectionException("The '" + prop.getName() + "' property of " + collection + " is not a List or Array.");
      }
    }
  }

}