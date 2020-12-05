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
package org.apache.ibatis.reflection.property;

import java.util.Iterator;

/**
 * @author Clinton Begin
 */
/**
 * 属性分词器： 用于解析表达式的值
 *
 */
public class PropertyTokenizer implements Iterable<PropertyTokenizer>, Iterator<PropertyTokenizer> {

  //如person[0].birthdate.year，将依次取得person[0], birthdate, year

  /**
   * 最"外层"的对象，也就是第一个 "." 前的对象， 上例为 person
   * */
  private String name;

  /**
   *  第一个 "." 之前的字符串，可以理解为当前分词器此次获取到的对象值， 上例为 person[0]
   * */
  private String indexedName;

  /**
   *  如果为集合的话， index 为 [] 之间的索引， 上例为 0
   * */
  private String index;

  /**
   *  第一个 "." 之后的字符串， 可以理解为当前获取到的对象值的某个属性, 上例为 birthdate.year
   * */
  private String children;

  /**
   * @param fullname 表达式
   *     对该表达式进行分词
   * */
  public PropertyTokenizer(String fullname) {
    int delim = fullname.indexOf('.');
    if (delim > -1) {
      name = fullname.substring(0, delim);
      children = fullname.substring(delim + 1);
    } else {
      name = fullname;
      children = null;
    }
    indexedName = name;
    delim = name.indexOf('[');
    if (delim > -1) {
      index = name.substring(delim + 1, name.length() - 1);
      name = name.substring(0, delim);
    }
  }

  public String getName() {
    return name;
  }

  public String getIndex() {
    return index;
  }

  public String getIndexedName() {
    return indexedName;
  }

  public String getChildren() {
    return children;
  }

  @Override
  public boolean hasNext() {
    return children != null;
  }

  /**
   *  next() 方法也就是对 孩子再次进行分词操作
   * */
  @Override
  public PropertyTokenizer next() {
    return new PropertyTokenizer(children);
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
  }

  @Override
  public Iterator<PropertyTokenizer> iterator() {
    return this;
  }
}
