/*
 *    Copyright 2009-2014 the original author or authors.
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
package org.apache.ibatis.binding;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.session.SqlSession;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
/**
 * dao对应的代理类： 在调用接口的方法时增加了实际的功能（执行对应的sql语句）
 *
 */
public class MapperProxy<T> implements InvocationHandler, Serializable {

  private static final long serialVersionUID = -6424540398559729838L;

  /**
   * 当前的sql会话
   * */
  private final SqlSession sqlSession;

  /**
   *  dao 接口对应的类对象
   * */
  private final Class<T> mapperInterface;

  /**
   *  key: dao接口对应的方法对象
   *  value: 代理方法
   * */
  private final Map<Method, MapperMethod> methodCache;

  public MapperProxy(SqlSession sqlSession, Class<T> mapperInterface, Map<Method, MapperMethod> methodCache) {
    this.sqlSession = sqlSession;
    this.mapperInterface = mapperInterface;
    this.methodCache = methodCache;
  }

  /**
   *  调用dao接口对应的方法
   * */
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    //1. 如果调用的方法的声明对象是 Object类，则直接执行
    if (Object.class.equals(method.getDeclaringClass())) {
      try {
        return method.invoke(this, args);
      } catch (Throwable t) {
        throw ExceptionUtil.unwrapThrowable(t);
      }
    }
    //2. 如果是dao接口声明的方法，则首先获取代理方法
    final MapperMethod mapperMethod = cachedMapperMethod(method);
    //3. 执行流程
    return mapperMethod.execute(sqlSession, args);
  }

  /**
   * @param method dao 接口声明的方法对象
   *    返回对应的代理方法对象
   * */
  private MapperMethod cachedMapperMethod(Method method) {
    MapperMethod mapperMethod = methodCache.get(method);
    if (mapperMethod == null) {
      mapperMethod = new MapperMethod(mapperInterface, method, sqlSession.getConfiguration());
      methodCache.put(method, mapperMethod);
    }
    return mapperMethod;
  }

}
