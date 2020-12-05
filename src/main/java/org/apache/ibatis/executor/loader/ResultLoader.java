/*
 *    Copyright 2009-2013 the original author or authors.
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
package org.apache.ibatis.executor.loader;

import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.ResultExtractor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.TransactionFactory;

/**
 * @author Clinton Begin
 */
/**
 * 结果延迟加载器，用来进行懒加载的加载器
 * 
 */
public class ResultLoader {

  /**
   *   mybatis 的全局配置类
   * */
  protected final Configuration configuration;

  // 执行器对象
  protected final Executor executor;

  //懒加载查询的mappedStatment
  protected final MappedStatement mappedStatement;

  //懒加载查询所需的参数
  protected final Object parameterObject;

  //内嵌查询的返回值类型
  protected final Class<?> targetType;

  //对象工厂
  protected final ObjectFactory objectFactory;

  // 内嵌查询对应的缓存key
  protected final CacheKey cacheKey;

  // 内嵌查询对应的绑定sql
  protected final BoundSql boundSql;

  // 结果抽取器
  protected final ResultExtractor resultExtractor;
  protected final long creatorThreadId;
  
  protected boolean loaded;
  protected Object resultObject;
  
  public ResultLoader(Configuration config, Executor executor, MappedStatement mappedStatement, Object parameterObject, Class<?> targetType, CacheKey cacheKey, BoundSql boundSql) {
    this.configuration = config;
    this.executor = executor;
    this.mappedStatement = mappedStatement;
    this.parameterObject = parameterObject;
    this.targetType = targetType;
    this.objectFactory = configuration.getObjectFactory();
    this.cacheKey = cacheKey;
    this.boundSql = boundSql;
    this.resultExtractor = new ResultExtractor(configuration, objectFactory);
    this.creatorThreadId = Thread.currentThread().getId();
  }

  /**
   *  加载结果对象
   * */
  public Object loadResult() throws SQLException {
	//1. 从数据库中查询结果
    List<Object> list = selectList();
    //2. 根据结果抽取器从查询结果中抽取真正的结果返回
    resultObject = resultExtractor.extractObjectFromList(list, targetType);
    return resultObject;
  }

  /**
   * 从数据库中查询结果
   * */
  private <E> List<E> selectList() throws SQLException {
    Executor localExecutor = executor;
    //1. 如果executor已经被关闭了，则创建一个新的
    if (Thread.currentThread().getId() != this.creatorThreadId || localExecutor.isClosed()) {
      localExecutor = newExecutor();
    }
    //2. 通过执行器查询结果
    try {
      return localExecutor.<E> query(mappedStatement, parameterObject, RowBounds.DEFAULT, Executor.NO_RESULT_HANDLER, cacheKey, boundSql);
    } finally {
      if (localExecutor != executor) {
        localExecutor.close(false);
      }
    }
  }

  /**
   *  构建一个新的（简单）执行器
   * */
  private Executor newExecutor() {
    final Environment environment = configuration.getEnvironment();
    if (environment == null) {
      throw new ExecutorException("ResultLoader could not load lazily.  Environment was not configured.");
    }
    final DataSource ds = environment.getDataSource();
    if (ds == null) {
      throw new ExecutorException("ResultLoader could not load lazily.  DataSource was not configured.");
    }
    final TransactionFactory transactionFactory = environment.getTransactionFactory();
    final Transaction tx = transactionFactory.newTransaction(ds, null, false);
    return configuration.newExecutor(tx, ExecutorType.SIMPLE);
  }

  public boolean wasNull() {
    return resultObject == null;
  }

}
