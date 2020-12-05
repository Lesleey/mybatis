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
package org.apache.ibatis.executor;

import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.mapping.MappedStatement;

/**
 *  批处理结果
 * @author Jeff Butler
 */
public class BatchResult {
  /**
   *  当前 sql 语句对应的 MappedStatement 对象
   * */
  private final MappedStatement mappedStatement;

  /**
   *  当前要执行的预编译的 Sql 语句
   * */
  private final String sql;

  /**
   *  批处理所传入的参数集合
   * */
  private final List<Object> parameterObjects;

  /**
   *  记录批处理所更新的行数
   * */
  private int[] updateCounts;

  public BatchResult(MappedStatement mappedStatement, String sql) {
    super();
    this.mappedStatement = mappedStatement;
    this.sql = sql;
    this.parameterObjects = new ArrayList<Object>();
  }

  public BatchResult(MappedStatement mappedStatement, String sql, Object parameterObject) {
    this(mappedStatement, sql);
    addParameterObject(parameterObject);
  }

  public MappedStatement getMappedStatement() {
    return mappedStatement;
  }

  public String getSql() {
    return sql;
  }

  @Deprecated
  public Object getParameterObject() {
    return parameterObjects.get(0);
  }

  public List<Object> getParameterObjects() {
    return parameterObjects;
  }

  public int[] getUpdateCounts() {
    return updateCounts;
  }

  public void setUpdateCounts(int[] updateCounts) {
    this.updateCounts = updateCounts;
  }

  public void addParameterObject(Object parameterObject) {
    this.parameterObjects.add(parameterObject);
  }

}
