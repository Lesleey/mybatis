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
package org.apache.ibatis.scripting.xmltags;

import java.util.HashMap;
import java.util.Map;

import ognl.OgnlContext;
import ognl.OgnlException;
import ognl.OgnlRuntime;
import ognl.PropertyAccessor;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
/**
 * 动态上下文
 * 
 */
public class DynamicContext {

  public static final String PARAMETER_OBJECT_KEY = "_parameter";
  public static final String DATABASE_ID_KEY = "_databaseId";

  static {
    /**
     *  你可以使用如下方法设置针对某个具体类的属性访问器
     * */
    OgnlRuntime.setPropertyAccessor(ContextMap.class, new ContextAccessor());
  }

  /**
   *  存储预编译所使用的参数
   * */
  private final ContextMap bindings;

  /**
   *  用于生成 sql 语句
   * */
  private final StringBuilder sqlBuilder = new StringBuilder();

  /**
   *  生成唯一的数字，用于foreach 标签生成唯一的属性名， 进行预编译设置参数
   * */
  private int uniqueNumber = 0;

  /**
   *  统一参数的访问方式:用 Map 接口访问数据.
   *  根据传入的参数类型，构造ContextMap时， 使用不同的构造函数
   * */
  public DynamicContext(Configuration configuration, Object parameterObject) {
    //1. 如果参数对象不为空，且不为 Map类型时
    if (parameterObject != null && !(parameterObject instanceof Map)) {
      MetaObject metaObject = configuration.newMetaObject(parameterObject);
      bindings = new ContextMap(metaObject);
    //2. 否则
    } else {
      bindings = new ContextMap(null);
    }
    //3. 存储参数对象和 databaseId
    bindings.put(PARAMETER_OBJECT_KEY, parameterObject);
    bindings.put(DATABASE_ID_KEY, configuration.getDatabaseId());
  }

  public Map<String, Object> getBindings() {
    return bindings;
  }

  /**
   * @param name 属性名
   * @param value 属性值
   *    向Ognl 中添加指定属性名和属性值之间映射关系，使用Ognl可以通过该属性名可以获取该属性值
   * */
  public void bind(String name, Object value) {
    bindings.put(name, value);
  }

  public void appendSql(String sql) {
    sqlBuilder.append(sql);
    sqlBuilder.append(" ");
  }

  public String getSql() {
    return sqlBuilder.toString().trim();
  }

  public int getUniqueNumber() {
    return uniqueNumber++;
  }


  static class ContextMap extends HashMap<String, Object> {
    private static final long serialVersionUID = 2977601501966151582L;

    /**
     *  参数对象的元数据
     * */
    private MetaObject parameterMetaObject;

    public ContextMap(MetaObject parameterMetaObject) {
      this.parameterMetaObject = parameterMetaObject;
    }

    @Override
    public Object get(Object key) {
      String strKey = (String) key;
      //先去map里找
      if (super.containsKey(strKey)) {
        return super.get(strKey);
      }

      //如果没找到，再用ognl表达式去取值
      //如person[0].birthdate.year
      if (parameterMetaObject != null) {
        // issue #61 do not modify the context when reading
        return parameterMetaObject.getValue(strKey);
      }

      return null;
    }
  }

  /**
   *  定义属性访问器，在OGNL中，寻找的所有的元素通常是某个javaBean的一些属性，但元素可以是很多东西，这取决于您具体要访问的对象，该类的作用就是自定义
   *  属性访问过程，将属性名称转化为对对象属性的实际访问的转换
   * */
  static class ContextAccessor implements PropertyAccessor {

    @Override
    public Object getProperty(Map context, Object target, Object name)
        throws OgnlException {
      //1. 首先将类型转化为实际类型 map
      Map map = (Map) target;
      //2. 将元素作为 key 从 map中寻找
      Object result = map.get(name);
      if (result != null) {
        return result;
      }
      //3. 从 map 中获取指定 key的值，如果该值为map类型，则将该元素作为该 value（map）的key进行寻找
      Object parameterObject = map.get(PARAMETER_OBJECT_KEY);
      if (parameterObject instanceof Map) {
        return ((Map)parameterObject).get(name);
      }

      return null;
    }

    @Override
    public void setProperty(Map context, Object target, Object name, Object value)
        throws OgnlException {
      Map<Object, Object> map = (Map<Object, Object>) target;
      map.put(name, value);
    }

    @Override
    public String getSourceAccessor(OgnlContext arg0, Object arg1, Object arg2) {
      return null;
    }

    @Override
    public String getSourceSetter(OgnlContext arg0, Object arg1, Object arg2) {
      return null;
    }
  }
}
