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
package org.apache.ibatis.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Clinton Begin
 */
/**
 * 类型处理器: 设置预处理语句（PreparedStatement）中的参数或从结果集中取出一个值时， 都会用类型处理器将获取到的值以合适的方式转换成 Java 类型
 * 
 */
public interface TypeHandler<T> {

  /**
   *  为预编译的sql语句设置参数
   * */
  void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException;

  /**
   *  从结果集中返回指定的列名的值
   * */
  T getResult(ResultSet rs, String columnName) throws SQLException;

  /**
   *  从结果集中返回指定列索引的值
   * */
  T getResult(ResultSet rs, int columnIndex) throws SQLException;

  /**
   *  TODO lesleey 从函数调用中返回指定列索引对应的值
   * */
  T getResult(CallableStatement cs, int columnIndex) throws SQLException;

}
