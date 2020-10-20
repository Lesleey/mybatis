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
package org.apache.ibatis.transaction;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Wraps a database connection.
 * Handles the connection lifecycle that comprises: its creation, preparation, commit/rollback and close.
 *   包装了一个数据源连接，处理该连接的生命周期： 创建、准备、提交|回滚、关闭
 *    mybatis 提供了包括两种类型的事务{@link org.apache.ibatis.transaction.jdbc.JdbcTransaction}
 *  和 {@link org.apache.ibatis.transaction.managed.ManagedTransaction}， 区别在于前者由 mybatis 本身负责管理事务
 *  （也就是数据库连接的提交和回滚）， 而后者由容器管理事务（比如spring）。
 *
 *
 * @author Clinton Begin
 */
public interface Transaction {

  /**
   * Retrieve inner database connection
   * @return DataBase connection
   * @throws SQLException
   */
  Connection getConnection() throws SQLException;

  /**
   * Commit inner database connection.
   * @throws SQLException
   */
  void commit() throws SQLException;

  /**
   * Rollback inner database connection.
   * @throws SQLException
   */
  void rollback() throws SQLException;

  /**
   * Close inner database connection.
   * @throws SQLException
   */
  void close() throws SQLException;

}
