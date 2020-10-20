/*
 *    Copyright 2009-2013 the original author or authors.
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
package org.apache.ibatis.type;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.io.Resources;

/**
 * @author Clinton Begin
 */
/**
 * 类型别名注册器
 * 
 */
public class TypeAliasRegistry {

  /**
   *  通过 map 存储别名
   * */
  private final Map<String, Class<?>> TYPE_ALIASES = new HashMap<String, Class<?>>();

  /**
   *  初始化该对象时，自动注册了一些别名
   * */
  public TypeAliasRegistry() {
    registerAlias("string", String.class);

	//基本包装类型
    registerAlias("byte", Byte.class);
    registerAlias("long", Long.class);
    registerAlias("short", Short.class);
    registerAlias("int", Integer.class);
    registerAlias("integer", Integer.class);
    registerAlias("double", Double.class);
    registerAlias("float", Float.class);
    registerAlias("boolean", Boolean.class);

	//基本数组包装类型
    registerAlias("byte[]", Byte[].class);
    registerAlias("long[]", Long[].class);
    registerAlias("short[]", Short[].class);
    registerAlias("int[]", Integer[].class);
    registerAlias("integer[]", Integer[].class);
    registerAlias("double[]", Double[].class);
    registerAlias("float[]", Float[].class);
    registerAlias("boolean[]", Boolean[].class);

	//加个下划线，就变成了基本类型
    registerAlias("_byte", byte.class);
    registerAlias("_long", long.class);
    registerAlias("_short", short.class);
    registerAlias("_int", int.class);
    registerAlias("_integer", int.class);
    registerAlias("_double", double.class);
    registerAlias("_float", float.class);
    registerAlias("_boolean", boolean.class);

	//加个下划线，就变成了基本数组类型
    registerAlias("_byte[]", byte[].class);
    registerAlias("_long[]", long[].class);
    registerAlias("_short[]", short[].class);
    registerAlias("_int[]", int[].class);
    registerAlias("_integer[]", int[].class);
    registerAlias("_double[]", double[].class);
    registerAlias("_float[]", float[].class);
    registerAlias("_boolean[]", boolean[].class);

	//日期数字型
    registerAlias("date", Date.class);
    registerAlias("decimal", BigDecimal.class);
    registerAlias("bigdecimal", BigDecimal.class);
    registerAlias("biginteger", BigInteger.class);
    registerAlias("object", Object.class);

    registerAlias("date[]", Date[].class);
    registerAlias("decimal[]", BigDecimal[].class);
    registerAlias("bigdecimal[]", BigDecimal[].class);
    registerAlias("biginteger[]", BigInteger[].class);
    registerAlias("object[]", Object[].class);

	//集合型
    registerAlias("map", Map.class);
    registerAlias("hashmap", HashMap.class);
    registerAlias("list", List.class);
    registerAlias("arraylist", ArrayList.class);
    registerAlias("collection", Collection.class);
    registerAlias("iterator", Iterator.class);

	//ResultSet型
    registerAlias("ResultSet", ResultSet.class);
  }

  /**
   * @param string  别名
   *   通过别名获取对应类的类对象
   * */
  public <T> Class<T> resolveAlias(String string) {
    try {
      if (string == null) {
        return null;
      }
      //https://code.google.com/p/mybatis/issues
      //1. 将别名转化为小写
      String key = string.toLowerCase(Locale.ENGLISH);
      Class<T> value;

      //2. 如果Map中包含该别名，直接返回对应的 value
      if (TYPE_ALIASES.containsKey(key)) {
        value = (Class<T>) TYPE_ALIASES.get(key);

      //3. 如果没有，使用别名进行反射，获取类对象。
      } else {
        value = (Class<T>) Resources.classForName(string);
      }
      return value;
    } catch (ClassNotFoundException e) {
      throw new TypeException("Could not resolve type alias '" + string + "'.  Cause: " + e, e);
    }
  }

  /**
   *  通过包名注册别名
   * */
  public void registerAliases(String packageName){
    registerAliases(packageName, Object.class);
  }


  /**
   * @param packageName 包名，进行注册别名的包名
   * @param superType 注册条件
   *    只有包中的类为该 superType的子类时，才会注册
   * */
  public void registerAliases(String packageName, Class<?> superType){

    //1. 使用 ResolverUtils 工具类获取该包下满足条件的所有类
    ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<Class<?>>();
    resolverUtil.find(new ResolverUtil.IsA(superType), packageName);
    Set<Class<? extends Class<?>>> typeSet = resolverUtil.getClasses();

    //2. 遍历所有满足条件的类，如果不是匿名内部类、接口或者成员类时才会进行注册
    for(Class<?> type : typeSet){
      if (!type.isAnonymousClass() && !type.isInterface() && !type.isMemberClass()) {
        registerAlias(type);
      }
    }
  }

  /**
   * @param type  通过类对象注册别名
   * */
  public void registerAlias(Class<?> type) {

    //1. 获取类的简单限定符作为默认别名
    String alias = type.getSimpleName();

    //2. 如果使用 Alias 注解指定了别名，则使用指定的值
    Alias aliasAnnotation = type.getAnnotation(Alias.class);
    if (aliasAnnotation != null) {
      alias = aliasAnnotation.value();
    }

    //3. 通过别名和类对象注册到类型注册器中
    registerAlias(alias, type);
  }

  /**
   * @param alias 别名
   * @param value 类对象
   * */
  public void registerAlias(String alias, Class<?> value) {
    if (alias == null) {
      throw new TypeException("The parameter alias cannot be null");
    }
    // issue #748
    //1. 将别名转化为小写
    String key = alias.toLowerCase(Locale.ENGLISH);

    //2. 如果该别名已经被使用了，而且对应的值和 参数 value 不同，则报错
    if (TYPE_ALIASES.containsKey(key) && TYPE_ALIASES.get(key) != null && !TYPE_ALIASES.get(key).equals(value)) {
      throw new TypeException("The alias '" + alias + "' is already mapped to the value '" + TYPE_ALIASES.get(key).getName() + "'.");
    }

    //3. 进行注册
    TYPE_ALIASES.put(key, value);
  }

  /**
   * @param alias 别名
   * @param value 类的全限定名称
   * 通过别名和类的全限定名称注册别名
   * */
  public void registerAlias(String alias, String value) {
    try {
      registerAlias(alias, Resources.classForName(value));
    } catch (ClassNotFoundException e) {
      throw new TypeException("Error registering type alias "+alias+" for "+value+". Cause: " + e, e);
    }
  }
  
  /**
   * @since 3.2.2
   *  将当前的保存别名和类对象的对应关系的map包装成不可修改的map返回。
   */

  public Map<String, Class<?>> getTypeAliases() {
    return Collections.unmodifiableMap(TYPE_ALIASES);
  }

}
