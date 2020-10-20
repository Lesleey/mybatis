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
package org.apache.ibatis.builder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeAliasRegistry;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;


/**
 *  构建器的基类
 */
public abstract class BaseBuilder {

  /**
   *  全局的配置对象
   * */
  protected final Configuration configuration;

  /**
   *  全局的类型别名注册器
   * */
  protected final TypeAliasRegistry typeAliasRegistry;

  /**
   *  全局的类型处理器
   * */
  protected final TypeHandlerRegistry typeHandlerRegistry;

  public BaseBuilder(Configuration configuration) {
    this.configuration = configuration;
    this.typeAliasRegistry = this.configuration.getTypeAliasRegistry();
    this.typeHandlerRegistry = this.configuration.getTypeHandlerRegistry();
  }

  public Configuration getConfiguration() {
    return configuration;
  }

  protected Pattern parseExpression(String regex, String defaultValue) {
    return Pattern.compile(regex == null ? defaultValue : regex);
  }

  protected Boolean booleanValueOf(String value, Boolean defaultValue) {
    return value == null ? defaultValue : Boolean.valueOf(value);
  }

  protected Integer integerValueOf(String value, Integer defaultValue) {
    return value == null ? defaultValue : Integer.valueOf(value);
  }

  /**
   *  将字符串转化为用逗号分割的集合。
   * */
  protected Set<String> stringSetValueOf(String value, String defaultValue) {
    value = (value == null ? defaultValue : value);
    return new HashSet<String>(Arrays.asList(value.split(",")));
  }


  /**
   *  通过 jdbc 别名获取对应的 JdbcType
   * */
  protected JdbcType resolveJdbcType(String alias) {
    if (alias == null) {
      return null;
    }
    try {
      return JdbcType.valueOf(alias);
    } catch (IllegalArgumentException e) {
      throw new BuilderException("Error resolving JdbcType. Cause: " + e, e);
    }
  }


 /**
  *  通过 resultSet 别名获取对应的 ResultType 类型
  * */
  protected ResultSetType resolveResultSetType(String alias) {
    if (alias == null) {
      return null;
    }
    try {
      return ResultSetType.valueOf(alias);
    } catch (IllegalArgumentException e) {
      throw new BuilderException("Error resolving ResultSetType. Cause: " + e, e);
    }
  }

  /**
   * 通过参数模式别名获取对应的 ParameterMode( IN | OUT | INOUT)
   * */
  protected ParameterMode resolveParameterMode(String alias) {
    if (alias == null) {
      return null;
    }
    try {
      return ParameterMode.valueOf(alias);
    } catch (IllegalArgumentException e) {
      throw new BuilderException("Error resolving ParameterMode. Cause: " + e, e);
    }
  }

 /**
  * 解析别名，获取对应的类对象，返回其实例
  * */
  protected Object createInstance(String alias) {
    Class<?> clazz = resolveClass(alias);
    if (clazz == null) {
      return null;
    }
    try {
      return resolveClass(alias).newInstance();
    } catch (Exception e) {
      throw new BuilderException("Error creating instance. Cause: " + e, e);
    }
  }


  /**
   *  解析别名，获取对应的类对象
   * */
  protected Class<?> resolveClass(String alias) {
    if (alias == null) {
      return null;
    }
    try {
      return resolveAlias(alias);
    } catch (Exception e) {
      throw new BuilderException("Error resolving class. Cause: " + e, e);
    }
  }

  /**
   *  指定Java类型和类型处理器别名，获取处理该java类型的类型处理器
   * */
  protected TypeHandler<?> resolveTypeHandler(Class<?> javaType, String typeHandlerAlias) {
    if (typeHandlerAlias == null) {
      return null;
    }
    //1. 通过类型处理器的别名获取对应的类对象
    Class<?> type = resolveClass(typeHandlerAlias);
    if (type != null && !TypeHandler.class.isAssignableFrom(type)) {
      throw new BuilderException("Type " + type.getName() + " is not a valid TypeHandler because it does not implement TypeHandler interface");
    }
    Class<? extends TypeHandler<?>> typeHandlerType = (Class<? extends TypeHandler<?>>) type;

    //2. 调用重载的方法获取类型处理器
    return resolveTypeHandler(javaType, typeHandlerType);
  }

  /**
   * 指定JAVA类型和类型处理器的类对象获取对应的类型处理器
   * */
  protected TypeHandler<?> resolveTypeHandler(Class<?> javaType, Class<? extends TypeHandler<?>> typeHandlerType) {
    if (typeHandlerType == null) {
      return null;
    }
    // javaType ignored for injected handlers see issue #746 for full detail
    //1. 从typeHandlerRegistry通过类型处理器类对象查询对应的TypeHandler
    TypeHandler<?> handler = typeHandlerRegistry.getMappingTypeHandler(typeHandlerType);

    if (handler == null) {
      //2. 如果没有找到，则根据Java类型和类型处理器类型实例化一个类型处理器对象
      handler = typeHandlerRegistry.getInstance(javaType, typeHandlerType);
    }
    return handler;
  }


  /**
   *  使用类型别名注册器解析别名获取对应的类对象
   * */
  protected Class<?> resolveAlias(String alias) {
    return typeAliasRegistry.resolveAlias(alias);
  }
}
