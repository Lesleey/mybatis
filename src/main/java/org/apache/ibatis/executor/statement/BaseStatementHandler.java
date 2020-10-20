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
package org.apache.ibatis.executor.statement;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 */
/**
 * 语句处理器的基类，对三种语句处理器相同的方法和构造提供了默认 的实现
 * 
 */
public abstract class BaseStatementHandler implements StatementHandler {
  //配置对象
  protected final Configuration configuration;
  //对象工厂。用来创建对象用的
  protected final ObjectFactory objectFactory;
  //类型处理注册器，typeHandler用来处理jdbcType <-> javaType 之间的映射
  protected final TypeHandlerRegistry typeHandlerRegistry;
  //结果集处理器，用来封装返回的结果集的
  protected final ResultSetHandler resultSetHandler;
  //参数处理器，用来给sql语句设置参数的
  protected final ParameterHandler parameterHandler;
  //执行器
  protected final Executor executor;
  //sql的映射语句
  protected final MappedStatement mappedStatement;
  protected final RowBounds rowBounds;

  protected BoundSql boundSql;

  protected BaseStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    this.configuration = mappedStatement.getConfiguration();
    this.executor = executor;
    this.mappedStatement = mappedStatement;
    this.rowBounds = rowBounds;

    this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
    this.objectFactory = configuration.getObjectFactory();

    if (boundSql == null) { // issue #435, get the key before calculating the statement
      generateKeys(parameterObject);
      boundSql = mappedStatement.getBoundSql(parameterObject);
    }

    this.boundSql = boundSql;

    //生成parameterHandler
    this.parameterHandler = configuration.newParameterHandler(mappedStatement, parameterObject, boundSql);
    //生成resultSetHandler
    this.resultSetHandler = configuration.newResultSetHandler(executor, mappedStatement, rowBounds, parameterHandler, resultHandler, boundSql);
  }

  @Override
  public BoundSql getBoundSql() {
    return boundSql;
  }

  @Override
  public ParameterHandler getParameterHandler() {
    return parameterHandler;
  }

  //准备语句，实例化statment,设置超时时间，设置读取条数
  @Override
  public Statement prepare(Connection connection) throws SQLException {
    ErrorContext.instance().sql(boundSql.getSql());
    Statement statement = null;
    try {
      //实例化Statement
      statement = instantiateStatement(connection);
      //设置超时
      setStatementTimeout(statement);
      //设置读取条数
      setFetchSize(statement);
      return statement;
    } catch (SQLException e) {
      closeStatement(statement);
      throw e;
    } catch (Exception e) {
      closeStatement(statement);
      throw new ExecutorException("Error preparing statement.  Cause: " + e, e);
    }
  }

  //如何实例化Statement，交给子类做，三个子类有不同的做法
  protected abstract Statement instantiateStatement(Connection connection) throws SQLException;

  //设置超时,其实就是调用Statement.setQueryTimeout
  protected void setStatementTimeout(Statement stmt) throws SQLException {
    Integer timeout = mappedStatement.getTimeout();
    Integer defaultTimeout = configuration.getDefaultStatementTimeout();
    if (timeout != null) {
      stmt.setQueryTimeout(timeout);
    } else if (defaultTimeout != null) {
      stmt.setQueryTimeout(defaultTimeout);
    }
  }

  //设置读取条数,其实就是调用Statement.setFetchSize
  protected void setFetchSize(Statement stmt) throws SQLException {
    Integer fetchSize = mappedStatement.getFetchSize();
    if (fetchSize != null) {
      stmt.setFetchSize(fetchSize);
    }
  }

  //关闭语句
  protected void closeStatement(Statement statement) {
    try {
      if (statement != null) {
        statement.close();
      }
    } catch (SQLException e) {
      //ignore
    }
  }

  //给参数设置值，这里是为了获取自增的主键
  protected void generateKeys(Object parameter) {
    KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
    ErrorContext.instance().store();
    keyGenerator.processBefore(executor, mappedStatement, null, parameter);
    ErrorContext.instance().recall();
  }

}
