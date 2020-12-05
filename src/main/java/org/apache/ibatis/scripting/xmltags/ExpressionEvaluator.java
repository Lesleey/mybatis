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

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.builder.BuilderException;

/**
 * @author Clinton Begin
 */
/**
 *  表达式求值器
 */
public class ExpressionEvaluator {

  /**
   *  解析表达式返回的布尔值, 用于动态 <sql/> 节点, 例如 params.key != null
   * */
  public boolean evaluateBoolean(String expression, Object parameterObject) {
    //1. 获取表达式的值
    Object value = OgnlCache.getValue(expression, parameterObject);
    //2. 如果为布尔类型，进行类型转换
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    //3. 如果为数值类型，不为0，则为true
    if (value instanceof Number) {
        return !new BigDecimal(String.valueOf(value)).equals(BigDecimal.ZERO);
    }
    //4. 如果为其他类型，不为null，则为true
    return value != null;
  }

    /**
     *  解析表达式，获取集合， 用于 <forEach/> 节点，例如 collection = "array"
     * */
  public Iterable<?> evaluateIterable(String expression, Object parameterObject) {
    //1. 获取表达式的值
    Object value = OgnlCache.getValue(expression, parameterObject);
    //2. 如果为null, 抛出异常
    if (value == null) {
      throw new BuilderException("The expression '" + expression + "' evaluated to a null value.");
    }
    //3. 如果集合，则直接返回
    if (value instanceof Iterable) {
      return (Iterable<?>) value;
    }
    //4.如果为数组，则新建一个集合，返回
    if (value.getClass().isArray()) {
    	//不能用Arrays.asList()，因为array可能是基本型，这样会出ClassCastException，
    	//见https://code.google.com/p/ibatis/issues/detail?id=209
        // the array may be primitive, so Arrays.asList() may throw
        // a ClassCastException (issue 209).  Do the work manually
        // Curse primitives! :) (JGB)
        int size = Array.getLength(value);
        List<Object> answer = new ArrayList<Object>();
        for (int i = 0; i < size; i++) {
            Object o = Array.get(value, i);
            answer.add(o);
        }
        return answer;
    }
    //5. 如果为map，则返回对应的 map.entry集合
    if (value instanceof Map) {
      return ((Map) value).entrySet();
    }
    throw new BuilderException("Error evaluating expression '" + expression + "'.  Return value (" + value + ") was not iterable.");
  }

}
