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
package org.apache.ibatis.session.defaults;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.exceptions.TooManyResultsException;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.result.DefaultMapResultHandler;
import org.apache.ibatis.executor.result.DefaultResultContext;
import org.apache.ibatis.binding.BindingException;
import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.exceptions.TooManyResultsException;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.result.DefaultMapResultHandler;
import org.apache.ibatis.executor.result.DefaultResultContext;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

/**
 * @author Clinton Begin
 */
/**
 * 默认SqlSession实现
 *
 */
public class DefaultSqlSession implements SqlSession {

  /**
   *  全局配置类
   * */
  private Configuration configuration;

  /**
   *  执行器实例
   * */
  private Executor executor;

  /**
   * 是否自动提交
   */
  private boolean autoCommit;

  /**
   *  是否有脏数据
   * */
  private boolean dirty;
  
  public DefaultSqlSession(Configuration configuration, Executor executor, boolean autoCommit) {
    this.configuration = configuration;
    this.executor = executor;
    this.dirty = false;
    this.autoCommit = autoCommit;
  }

  public DefaultSqlSession(Configuration configuration, Executor executor) {
    this(configuration, executor, false);
  }

  @Override
  public <T> T selectOne(String statement) {
    return this.<T>selectOne(statement, null);
  }

  /**
   *  查询一条记录
   * */
  @Override
  public <T> T selectOne(String statement, Object parameter) {
    // Popular vote was to return null on 0 results and throw exception on too many.
    //1. 调用 selectList 方法查询结果
    List<T> list = this.<T>selectList(statement, parameter);
    //2. 如果结果只有一条，则返回该条数据
    if (list.size() == 1) {
      return list.get(0);
    //3. 如果结果有多条，抛出 TooManyResultException
    } else if (list.size() > 1) {
      throw new TooManyResultsException("Expected one result (or null) to be returned by selectOne(), but found: " + list.size());
    //4. 如果为空集合，则返回null
    } else {
      return null;
    }
  }

  @Override
  public <K, V> Map<K, V> selectMap(String statement, String mapKey) {
    return this.selectMap(statement, null, mapKey, RowBounds.DEFAULT);
  }

  @Override
  public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey) {
    return this.selectMap(statement, parameter, mapKey, RowBounds.DEFAULT);
  }

  /**
   *  查询记录，返回值为 map 类型
   * */
  @Override
  public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey, RowBounds rowBounds) {
    //1. 调用 selectList 方法查询结果
    final List<?> list = selectList(statement, parameter, rowBounds);
    //2. 构建 Map 结果处理器, 处理返回的 list 集合
    final DefaultMapResultHandler<K, V> mapResultHandler = new DefaultMapResultHandler<K, V>(mapKey,
        configuration.getObjectFactory(), configuration.getObjectWrapperFactory());
    final DefaultResultContext context = new DefaultResultContext();
    for (Object o : list) {
      context.nextResultObject(o);
      mapResultHandler.handleResult(context);
    }
    //3. 返回最终的结果 @MapKey 指定的 Ognl表达式的值作为key, 该条记录的解析结果作为值
    return mapResultHandler.getMappedResults();
  }

  @Override
  public <E> List<E> selectList(String statement) {
    return this.selectList(statement, null);
  }

  @Override
  public <E> List<E> selectList(String statement, Object parameter) {
    return this.selectList(statement, parameter, RowBounds.DEFAULT);
  }

  /**
   *  核心方法： 所有的查询方法(不带 ResultHandler 类型的参数)实际上都委托给该方法
   * */
  @Override
  public <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds) {
    try {
      //1. 获取 当前执行的sql语句对应的 MappedStatement 对象
      MappedStatement ms = configuration.getMappedStatement(statement);
      //2. 通过执行器查询结果
      return executor.query(ms, wrapCollection(parameter), rowBounds, Executor.NO_RESULT_HANDLER);
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error querying database.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  @Override
  public void select(String statement, Object parameter, ResultHandler handler) {
    select(statement, parameter, RowBounds.DEFAULT, handler);
  }

  @Override
  public void select(String statement, ResultHandler handler) {
    select(statement, null, RowBounds.DEFAULT, handler);
  }

  //核心select,带有ResultHandler，和selectList代码差不多的，区别就一个ResultHandler
  @Override
  public void select(String statement, Object parameter, RowBounds rowBounds, ResultHandler handler) {
    try {
      MappedStatement ms = configuration.getMappedStatement(statement);
      executor.query(ms, wrapCollection(parameter), rowBounds, handler);
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error querying database.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  @Override
  public int insert(String statement) {
    return insert(statement, null);
  }

  @Override
  public int insert(String statement, Object parameter) {
    return update(statement, parameter);
  }

  @Override
  public int update(String statement) {
    return update(statement, null);
  }

  /**
   *  insert | delete | update 语句都会调用该方法
   * @param statement sql 语句节点标识的唯一标识
   * @param parameter 传入的参数
   * */
  @Override
  public int update(String statement, Object parameter) {
    try {
      //1. 标记 dirty 为true, 表示有脏数据
      dirty = true;
      MappedStatement ms = configuration.getMappedStatement(statement);
      //2. 通过执行器执行更新
      return executor.update(ms, wrapCollection(parameter));
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error updating database.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  @Override
  public int delete(String statement) {
    return update(statement, null);
  }

  @Override
  public int delete(String statement, Object parameter) {
    return update(statement, parameter);
  }

  @Override
  public void commit() {
    commit(false);
  }

  //核心commit
  @Override
  public void commit(boolean force) {
    try {
      //转而用执行器来commit
      executor.commit(isCommitOrRollbackRequired(force));
      //每次commit之后，dirty标志设为false
      dirty = false;
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error committing transaction.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  @Override
  public void rollback() {
    rollback(false);
  }

  //核心rollback
  @Override
  public void rollback(boolean force) {
    try {
      //转而用执行器来rollback
      executor.rollback(isCommitOrRollbackRequired(force));
      //每次rollback之后，dirty标志设为false
      dirty = false;
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error rolling back transaction.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  //核心flushStatements
  @Override
  public List<BatchResult> flushStatements() {
    try {
      //转而用执行器来flushStatements
      return executor.flushStatements();
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error flushing statements.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  //核心close
  @Override
  public void close() {
    try {
      //转而用执行器来close
      executor.close(isCommitOrRollbackRequired(false));
      //每次close之后，dirty标志设为false
      dirty = false;
    } finally {
      ErrorContext.instance().reset();
    }
  }

  @Override
  public Configuration getConfiguration() {
    return configuration;
  }

  /**
   * 获取dao类对应的动态代理方法，使得调用接口的方法时调用 mapper 文件对应的sql语句
   * */
  @Override
  public <T> T getMapper(Class<T> type) {
    return configuration.<T>getMapper(type, this);
  }

  @Override
  public Connection getConnection() {
    try {
      return executor.getTransaction().getConnection();
    } catch (SQLException e) {
      throw ExceptionFactory.wrapException("Error getting a new connection.  Cause: " + e, e);
    }
  }

  //核心clearCache
  @Override
  public void clearCache() {
    //转而用执行器来clearLocalCache
    executor.clearLocalCache();
  }

  //检查是否需要强制commit或rollback
  private boolean isCommitOrRollbackRequired(boolean force) {
    return (!autoCommit && dirty) || force;
  }

  /**
   *  如果参数为集合，则通过map进行包装，否则直接返回
   *    1. 如果参数为 Collection类型，map的key: "collection"
   *    2. 如果参数为 List 类型, key: "list"
   *    3. 如果参数为 数组， key:"array"
   *    value: 为参数的值
   * */
  private Object wrapCollection(final Object object) {
    if (object instanceof Collection) {
      StrictMap<Object> map = new StrictMap<Object>();
      map.put("collection", object);
      if (object instanceof List) {
        map.put("list", object);
      }
      return map;      
    } else if (object != null && object.getClass().isArray()) {
      StrictMap<Object> map = new StrictMap<Object>();
      map.put("array", object);
      return map;
    }
    return object;
  }

  /**
   *  自定义的map， 如果找不到对应的key,直接抛出异常
   * */
  public static class StrictMap<V> extends HashMap<String, V> {

    private static final long serialVersionUID = -5741767162221585340L;

    @Override
    public V get(Object key) {
      if (!super.containsKey(key)) {
        throw new BindingException("Parameter '" + key + "' not found. Available parameters are " + this.keySet());
      }
      return super.get(key);
    }

  }

}
