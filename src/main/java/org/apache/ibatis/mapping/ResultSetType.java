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
package org.apache.ibatis.mapping;

import java.sql.ResultSet;

/**
 * @author Clinton Begin
 */
/**
 * 结果集类型
 */
public enum ResultSetType {
  /**
   *  表示数据库的游标只能向后移动，从第一行到最后一行，不允许向前移动
   * */
  FORWARD_ONLY(ResultSet.TYPE_FORWARD_ONLY),

  /**
   *  数据库的游标可向前或者向后移动，或者可以指定移动到的位置，当 ResultSet没有关闭时，ResultSet 的修改对数据库不敏感，意思是修改不会影响到数据库
   * */
  SCROLL_INSENSITIVE(ResultSet.TYPE_SCROLL_INSENSITIVE),

  /**
   *  该类型与上一种的类型的唯一区别是当 ResultSet 没有关闭时，对其修改会影响到数据库的记录
   * */
  SCROLL_SENSITIVE(ResultSet.TYPE_SCROLL_SENSITIVE);

  private int value;

  ResultSetType(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
