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

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.executor.BaseExecutor;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * @author Clinton Begin
 * @author Franta Mejta
 */
/**
 * 结果延迟加载器映射,存储所有需要延迟加载的属性。
 * 
 */
public class ResultLoaderMap {

  /**
   *  加载对
   *   key: 属性名称， value: 加载对
   * */
  private final Map<String, LoadPair> loaderMap = new HashMap<String, LoadPair>();


  public void addLoader(String property, MetaObject metaResultObject, ResultLoader resultLoader) {
    String upperFirst = getUppercaseFirstProperty(property);
    if (!upperFirst.equalsIgnoreCase(property) && loaderMap.containsKey(upperFirst)) {
      throw new ExecutorException("Nested lazy loaded result property '" + property +
              "' for query id '" + resultLoader.mappedStatement.getId() +
              " already exists in the result map. The leftmost property of all lazy loaded properties must be unique within a result map.");
    }
    loaderMap.put(upperFirst, new LoadPair(property, metaResultObject, resultLoader));
  }

  public final Map<String, LoadPair> getProperties() {
    return new HashMap<String, LoadPair>(this.loaderMap);
  }

  public Set<String> getPropertyNames() {
    return loaderMap.keySet();
  }

  public int size() {
    return loaderMap.size();
  }

  public boolean hasLoader(String property) {
    return loaderMap.containsKey(property.toUpperCase(Locale.ENGLISH));
  }

  public boolean load(String property) throws SQLException {
	//1. 先删除key，防止第二次查询数据库
    LoadPair pair = loaderMap.remove(property.toUpperCase(Locale.ENGLISH));
    //2. 从数据库中查询加载对指定的属性
    if (pair != null) {
      pair.load();
      return true;
    }
    return false;
  }

  /**
   *  加载给结果加载器里的所有加载对
   * */
  public void loadAll() throws SQLException {
    final Set<String> methodNameSet = loaderMap.keySet();
    String[] methodNames = methodNameSet.toArray(new String[methodNameSet.size()]);
    for (String methodName : methodNames) {
      load(methodName);
    }
  }

  private static String getUppercaseFirstProperty(String property) {
    String[] parts = property.split("\\.");
    return parts[0].toUpperCase(Locale.ENGLISH);
  }

  /**
   * Property which was not loaded yet.
   *  静态内部类，加载对
   */
  public static class LoadPair implements Serializable {

    private static final long serialVersionUID = 20130412;
    /**
     *  返回数据库连接的工厂方法名
     */
    private static final String FACTORY_METHOD = "getConfiguration";
    /**
     * Object to check whether we went through serialization..
     */
    private final transient Object serializationCheck = new Object();
    /**
     *  用来设置懒加载属性的元对象
     */
    private transient MetaObject metaResultObject;
    /**
     * 加载懒加载的属性的结果加载器
     */
    private transient ResultLoader resultLoader;

    private transient Log log;
    /**
     * 获得数据库连接的配置工厂类对象
     */
    private Class<?> configurationFactory;
    /**
     * 懒加载的属性
     */
    private String property;
    /**
     * 进行懒加载sql语句对应的 MappedStatement 的id
     */
    private String mappedStatement;
    /**
     * 该sql语句的需要的参数
     */
    private Serializable mappedParameter;

    private LoadPair(final String property, MetaObject metaResultObject, ResultLoader resultLoader) {
      this.property = property;
      this.metaResultObject = metaResultObject;
      this.resultLoader = resultLoader;

      /* Save required information only if original object can be serialized. */
      if (metaResultObject != null && metaResultObject.getOriginalObject() instanceof Serializable) {
        final Object mappedStatementParameter = resultLoader.parameterObject;

        /* @todo May the parameter be null? */
        if (mappedStatementParameter instanceof Serializable) {
          this.mappedStatement = resultLoader.mappedStatement.getId();
          this.mappedParameter = (Serializable) mappedStatementParameter;

          this.configurationFactory = resultLoader.configuration.getConfigurationFactory();
        } else {
          this.getLogger().debug("Property [" + this.property + "] of ["
                  + metaResultObject.getOriginalObject().getClass() + "] cannot be loaded "
                  + "after deserialization. Make sure it's loaded before serializing "
                  + "forenamed object.");
        }
      }
    }

    public void load() throws SQLException {
      /* These field should not be null unless the loadpair was serialized.
       * Yet in that case this method should not be called. */
      if (this.metaResultObject == null) {
        throw new IllegalArgumentException("metaResultObject is null");
      }
      if (this.resultLoader == null) {
        throw new IllegalArgumentException("resultLoader is null");
      }

      this.load(null);
    }

    /**
     *  懒加载的主要流程
     * */
    public void load(final Object userObject) throws SQLException {
      if (this.metaResultObject == null || this.resultLoader == null) {
        if (this.mappedParameter == null) {
          throw new ExecutorException("Property [" + this.property + "] cannot be loaded because "
                  + "required parameter of mapped statement ["
                  + this.mappedStatement + "] is not serializable.");
        }
        //1. 获取 mybatis 的全局配置类
        final Configuration config = this.getConfiguration();
        //2. 获取待执行的 sql 语句对应的 MappedStatement 对象
        final MappedStatement ms = config.getMappedStatement(this.mappedStatement);
        if (ms == null) {
          throw new ExecutorException("Cannot lazy load property [" + this.property
                  + "] of deserialized object [" + userObject.getClass()
                  + "] because configuration does not contain statement ["
                  + this.mappedStatement + "]");
        }
        //3. 构建 userObject 的元对象
        this.metaResultObject = config.newMetaObject(userObject);
        //4. 构建结果加载器
        this.resultLoader = new ResultLoader(config, new ClosedExecutor(), ms, this.mappedParameter,
                metaResultObject.getSetterType(this.property), null, null);
      }

      /* We are using a new executor because we may be (and likely are) on a new thread
       * and executors aren't thread safe. (Is this sufficient?)
       *
       * A better approach would be making executors thread safe. */
      // 5. 加载对的线程安全问题(太六了)
      if (this.serializationCheck == null) {
        final ResultLoader old = this.resultLoader;
        this.resultLoader = new ResultLoader(old.configuration, new ClosedExecutor(), old.mappedStatement,
                old.parameterObject, old.targetType, old.cacheKey, old.boundSql);
      }

      this.metaResultObject.setValue(property, this.resultLoader.loadResult());
    }

    /**
     *  获取 MyBatis 的全局配置类
     * */
    private Configuration getConfiguration() {
      if (this.configurationFactory == null) {
        throw new ExecutorException("Cannot get Configuration as configuration factory was not set.");
      }

      Object configurationObject = null;
      try {
        final Method factoryMethod = this.configurationFactory.getDeclaredMethod(FACTORY_METHOD);
        if (!Modifier.isStatic(factoryMethod.getModifiers())) {
          throw new ExecutorException("Cannot get Configuration as factory method ["
                  + this.configurationFactory + "]#["
                  + FACTORY_METHOD + "] is not static.");
        }

        if (!factoryMethod.isAccessible()) {
          configurationObject = AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
            @Override
            public Object run() throws Exception {
              try {
                factoryMethod.setAccessible(true);
                return factoryMethod.invoke(null);
              } finally {
                factoryMethod.setAccessible(false);
              }
            }
          });
        } else {
          configurationObject = factoryMethod.invoke(null);
        }
      } catch (final NoSuchMethodException ex) {
        throw new ExecutorException("Cannot get Configuration as factory class ["
                + this.configurationFactory + "] is missing factory method of name ["
                + FACTORY_METHOD + "].", ex);
      } catch (final PrivilegedActionException ex) {
        throw new ExecutorException("Cannot get Configuration as factory method ["
                + this.configurationFactory + "]#["
                + FACTORY_METHOD + "] threw an exception.", ex.getCause());
      } catch (final Exception ex) {
        throw new ExecutorException("Cannot get Configuration as factory method ["
                + this.configurationFactory + "]#["
                + FACTORY_METHOD + "] threw an exception.", ex);
      }

      if (!(configurationObject instanceof Configuration)) {
        throw new ExecutorException("Cannot get Configuration as factory method ["
                + this.configurationFactory + "]#["
                + FACTORY_METHOD + "] didn't return [" + Configuration.class + "] but ["
                + (configurationObject == null ? "null" : configurationObject.getClass()) + "].");
      }

      return Configuration.class.cast(configurationObject);
    }

    private Log getLogger() {
      if (this.log == null) {
        this.log = LogFactory.getLog(this.getClass());
      }
      return this.log;
    }
  }

  /**
   *   关闭执行器
   * */
  private static final class ClosedExecutor extends BaseExecutor {

    public ClosedExecutor() {
      super(null, null);
    }

    @Override
    public boolean isClosed() {
      return true;
    }

    @Override
    protected int doUpdate(MappedStatement ms, Object parameter) throws SQLException {
      throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    protected List<BatchResult> doFlushStatements(boolean isRollback) throws SQLException {
      throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    protected <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
      throw new UnsupportedOperationException("Not supported.");
    }
  }
}
