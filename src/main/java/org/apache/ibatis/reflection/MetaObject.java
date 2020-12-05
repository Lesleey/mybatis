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
package org.apache.ibatis.reflection;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.reflection.wrapper.*;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.reflection.wrapper.BeanWrapper;
import org.apache.ibatis.reflection.wrapper.CollectionWrapper;
import org.apache.ibatis.reflection.wrapper.MapWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;

/**
 * @author Clinton Begin
 */
/**
 * 元对象
 *
 */
public class MetaObject {

  /**
   *  原对象
   * */
  private Object originalObject;

  /**
   *  对象包装器，用于获取、设置属性的值
   * */
  private ObjectWrapper objectWrapper;

  /**
   *  对象工厂，用于初始化对象
   * */
  private ObjectFactory objectFactory;

  /**
   *  对象包装工厂： 用于获取对象包装器
   * */
  private ObjectWrapperFactory objectWrapperFactory;

  private MetaObject(Object object, ObjectFactory objectFactory, ObjectWrapperFactory objectWrapperFactory) {
    this.originalObject = object;
    this.objectFactory = objectFactory;
    this.objectWrapperFactory = objectWrapperFactory;

    //1. 如果对象本身已经是ObjectWrapper型，则直接赋给objectWrapper
    if (object instanceof ObjectWrapper) {
      this.objectWrapper = (ObjectWrapper) object;

    //2. 如果对象包装工厂中包含该原对象的包装器，则获取并赋值
    } else if (objectWrapperFactory.hasWrapperFor(object)) {
      this.objectWrapper = objectWrapperFactory.getWrapperFor(this, object);
    //3.，否则根据object的类型，创建不同的对象包装器。
    } else if (object instanceof Map) {
      this.objectWrapper = new MapWrapper(this, (Map) object);
    } else if (object instanceof Collection) {
      this.objectWrapper = new CollectionWrapper(this, (Collection) object);
    } else {
      this.objectWrapper = new BeanWrapper(this, object);
    }
  }


  public static MetaObject forObject(Object object, ObjectFactory objectFactory, ObjectWrapperFactory objectWrapperFactory) {
    if (object == null) {
      return SystemMetaObject.NULL_META_OBJECT;
    } else {
      return new MetaObject(object, objectFactory, objectWrapperFactory);
    }
  }
  public ObjectFactory getObjectFactory() {
    return objectFactory;
  }
  public ObjectWrapperFactory getObjectWrapperFactory() {
    return objectWrapperFactory;
  }
  public Object getOriginalObject() {
    return originalObject;
  }

  //查找是否包含该字段
  public String findProperty(String propName, boolean useCamelCaseMapping) {
    return objectWrapper.findProperty(propName, useCamelCaseMapping);
  }

  //取得可读的字段名称
  public String[] getGetterNames() {
    return objectWrapper.getGetterNames();
  }

  //取得可写的字段名称
  public String[] getSetterNames() {
    return objectWrapper.getSetterNames();
  }

  //取得可写的字段类型（如果有set方法，则为参数类型）
  public Class<?> getSetterType(String name) {
    return objectWrapper.getSetterType(name);
  }

  //取得可读的字段类型（如果有get方法，则为返回值类型）
  public Class<?> getGetterType(String name) {
    return objectWrapper.getGetterType(name);
  }
  //判断是该字段是否有 set调用者
  public boolean hasSetter(String name) {
    return objectWrapper.hasSetter(name);
  }
  //判断该字段是否有 get调用者
  public boolean hasGetter(String name) {
    return objectWrapper.hasGetter(name);
  }


  /**
   * @param name ognl表达式
   *
   * */
  public Object getValue(String name) {
    //1. 通过表达式构建分词器
    PropertyTokenizer prop = new PropertyTokenizer(name);
    //2. 如果存在 "孩子"， 即 name 中包含 "."
    if (prop.hasNext()) {
      //2.1 获取 indexName(当前的元对象的一个属性)对应的值
      MetaObject metaValue = metaObjectForProperty(prop.getIndexedName());
      //2.2 如果不存在该值，则返回 null
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        return null;
      //2.3 使用 indexName 对应的值构建的元对象，递归的获取孩子的值
      } else {
       return metaValue.getValue(prop.getChildren());
      }
    //3. 否则表示name为当前元对象的一个属性，则直接获取该属性的值
    } else {
      return objectWrapper.get(prop);
    }
  }

  /**
   * @param name ognl表达式
   * @param value 设置的值
   * */
  public void setValue(String name, Object value) {
    //1. 使用该表达式构建分词器
    PropertyTokenizer prop = new PropertyTokenizer(name);
    //2. 如果存在 "孩子"
    if (prop.hasNext()) {
      //2.1 获取分词器 indexName 对应的值
      MetaObject metaValue = metaObjectForProperty(prop.getIndexedName());
      //2.2 如果该对象为空，判断有没有孩子，如果没有返回，否则通过包装器构建初始化该属性，然后在设值
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        if (value == null && prop.getChildren() != null) {
          // don't instantiate child path if value is null
          return;
        } else {
          metaValue = objectWrapper.instantiatePropertyValue(name, prop, objectFactory);
        }
      }
      metaValue.setValue(prop.getChildren(), value);
    //3. 如果不存在孩子，则表示 name 为当前元对象的一个属性，则直接赋值
    } else {
      objectWrapper.set(prop, value);
    }
  }

  /**
   * @param name 根据ognl表达式构建的分词器中的 "indexName"
   * */
  public MetaObject metaObjectForProperty(String name) {
    //1. 获取属性对应的值
    Object value = getValue(name);
    //2. 根据该值构建元对象
    return MetaObject.forObject(value, objectFactory, objectWrapperFactory);
  }

  public ObjectWrapper getObjectWrapper() {
    return objectWrapper;
  }

  //是否是集合
  public boolean isCollection() {
    return objectWrapper.isCollection();
  }
  
  //添加属性
  public void add(Object element) {
    objectWrapper.add(element);
  }

  //添加属性
  public <E> void addAll(List<E> list) {
    objectWrapper.addAll(list);
  }
  
}
