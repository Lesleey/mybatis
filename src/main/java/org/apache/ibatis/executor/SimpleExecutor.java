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
package org.apache.ibatis.executor;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * @author Clinton Begin
 */
/**
 * 简单执行器: 在获取预编译的sql语句时没有做任何其他的工作， 每次执行都会重新进行sql的预编译，设置参数等工作
 * 
 */
public class SimpleExecutor extends BaseExecutor {

  public SimpleExecutor(Configuration configuration, Transaction transaction) {
    super(configuration, transaction);
  }


  /**
   * @param ms 解析sql语句节点所构建的对象
   * @param parameter 参数对象
   *    执行 update | insert | delete 语句
   * */
  @Override
  public int doUpdate(MappedStatement ms, Object parameter) throws SQLException {
    Statement stmt = null;
    try {
      Configuration configuration = ms.getConfiguration();
      //1. 构建语句处理器
      StatementHandler handler = configuration.newStatementHandler(this, ms, parameter, RowBounds.DEFAULT, null, null);
      //2. 准备要执行的 Statement 语句
      stmt = prepareStatement(handler, ms.getStatementLog());
      //3. 执行更新
      return handler.update(stmt);
    } finally {
      closeStatement(stmt);
    }
  }

  /**
   *  执行 select 语句
   * */
  @Override
  public <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
    Statement stmt = null;
    try {
      //1. 准备sql，预编译，设置参数
      Configuration configuration = ms.getConfiguration();
      StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, resultHandler, boundSql);
      stmt = prepareStatement(handler, ms.getStatementLog());
      //2. 查询结果
      return handler.<E>query(stmt, resultHandler);
    } finally {
      closeStatement(stmt);
    }
  }

  /**
   *  刷新（执行）未执行的sql语句， 因为只有批量处理执行器会有，所有返回空
   * */
  @Override
  public List<BatchResult> doFlushStatements(boolean isRollback) throws SQLException {
    return Collections.emptyList();
  }


  /**
   *  准备要执行的sql语句： 获取连接、设置参数
   * */
  private Statement prepareStatement(StatementHandler handler, Log statementLog) throws SQLException {
    Statement stmt;
    //1. 通过反射，获取带日志的数据库连接
    Connection connection = getConnection(statementLog);
    //2. 通过语句处理器准备 Statement 对象
    stmt = handler.prepare(connection);
    //3. 通过语句处理器设置参数
    handler.parameterize(stmt);
    return stmt;
  }

}
