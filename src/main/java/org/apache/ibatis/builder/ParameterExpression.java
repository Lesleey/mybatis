/*
 * Copyright 2012-2013 MyBatis.org.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.builder;

import java.util.HashMap;

/**
 * Inline parameter expression parser. Supported grammar (simplified):
 * 
 * <pre>
 * inline-parameter = (propertyName | expression) oldJdbcType attributes
 * propertyName = /expression language's property navigation path/
 * expression = '(' /expression language's expression/ ')'
 * oldJdbcType = ':' /any valid jdbc type/
 * attributes = (',' attribute)*
 * attribute = name '=' value
 * </pre>
 */
/**
 * @author Frank D. Martinez [mnesarco]
 */
/**
 * 参数表达式,继承自HashMap
 */
public class ParameterExpression extends HashMap<String, String> {

  private static final long serialVersionUID = -2417552199605158680L;

  public ParameterExpression(String expression) {
    parse(expression);
  }

  /**
   *  #{property,javaType=int,jdbcType=NUMERIC}
   *  解析流程
   * */
  private void parse(String expression) {
    //1. 获取第一个不为控制字符的下标
    int p = skipWS(expression, 0);
    //2. 处理表达式
    if (expression.charAt(p) == '(') {
      expression(expression, p + 1);
    //3. 处理属性
    } else {
      property(expression, p);
    }
  }

  //表达式可能是3.2的新功能，可以先不管
  private void expression(String expression, int left) {
    int match = 1;
    int right = left + 1;
    while (match > 0) {
      if (expression.charAt(right) == ')') {
        match--;
      } else if (expression.charAt(right) == '(') {
        match++;
      }
      right++;
    }
    put("expression", expression.substring(left, right - 1));
    jdbcTypeOpt(expression, right);
  }

  /**
   * 解析属性
   * @param expression 字符串表达式
   * @param left 开始索引
   *  eg: #{name,javaType=int,jdbcType=NUMERIC} ==> put("property", "name")
   * */
  private void property(String expression, int left) {
    if (left < expression.length()) {
      //1. 获取表达式逗号或者冒号之前的字符串对应的索引
      int right = skipUntil(expression, left, ",:");
      //2. 获取对应的属性名称，添加到map中
      put("property", trimmedStr(expression, left, right));
      //3. 处理其他的参数，比如jdbcType， javaType等等
      jdbcTypeOpt(expression, right);
    }
  }

  /**
   * @param expression string 字符串
   * @param p  开始下标
   *     返回表达式的开始下标之后，第一个不为控制字符的下标，如果不存在返回expresssion的长度
   * */
  private int skipWS(String expression, int p) {
    for (int i = p; i < expression.length(); i++) {
      if (expression.charAt(i) > 0x20) {
        return i;
      }
    }
    return expression.length();
  }

  /**
   * @param expression 字符串表达式
   * @param p 开始下标
   * @param endChars 结束符号
   *    返回开始表达式的开始坐标之后，结束符号之前的符号对应的索引
   * */
  private int skipUntil(String expression, int p, final String endChars) {
    for (int i = p; i < expression.length(); i++) {
      char c = expression.charAt(i);
      if (endChars.indexOf(c) > -1) {
        return i;
      }
    }
    return expression.length();
  }

  /**
   *  解析jdbcType和其他的选项
   *
   * */
  private void jdbcTypeOpt(String expression, int p) {
    p = skipWS(expression, p);
    if (p < expression.length()) {
      //1. 如果 p 索引之后的非控制字符为 ":"， 则开始解析 jdbcType
      if (expression.charAt(p) == ':') {
        jdbcType(expression, p + 1);
      //2. 否则解析其他选项
      } else if (expression.charAt(p) == ',') {
        option(expression, p + 1);
      } else {
        throw new BuilderException("Parsing error in {" + new String(expression) + "} in position " + p);
      }
    }
  }

  /**
   *    解析占位符表达式参数的 jdbcType类型
   *  eg:  #{name:VARCHAR,javaType=Int} ==> put("jdbcType", "VARCHAR")
   * */
  private void jdbcType(String expression, int p) {
    //1. 添加 jdbcType 以及对应的值
    int left = skipWS(expression, p);
    int right = skipUntil(expression, left, ",");
    if (right > left) {
      put("jdbcType", trimmedStr(expression, left, right));
    } else {
      throw new BuilderException("Parsing error in {" + new String(expression) + "} in position " + p);
    }
    //2. 解析占位符的其他选项
    option(expression, right + 1);
  }

  /**
   *  解析占位符表达式其他选项
   *   eg: #{name,javaType=int,jdbcType=NUMERIC}
   *   put("javaType", "Int")  put("jdbcType", "NUMERIC")
   * */
  private void option(String expression, int p) {
    int left = skipWS(expression, p);
    if (left < expression.length()) {
      //1. 在p下标之后，寻找expression字符为"="的下标
      int right = skipUntil(expression, left, "=");
      //2. 获取该选项的参数名
      String name = trimmedStr(expression, left, right);
      left = right + 1;
      //3. 在left下标之后，寻找expression字符为"，"的下标
      right = skipUntil(expression, left, ",");
      //4. 获取该选项的参数值
      String value = trimmedStr(expression, left, right);
      //5. 将参数名和参数值添加到集合中
      put(name, value);
      //6. 递归
      option(expression, right + 1);
    }
  }

  /**
   *  去除 start 索引之前、end 索引之后的空格、回车等等控制字符
   * */
  private String trimmedStr(String str, int start, int end) {
    while (str.charAt(start) <= 0x20) {
      start++;
    }
    while (str.charAt(end - 1) <= 0x20) {
      end--;
    }
    return start >= end ? "" : str.substring(start, end);
  }

}
