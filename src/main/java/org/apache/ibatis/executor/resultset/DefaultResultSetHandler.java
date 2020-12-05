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
package org.apache.ibatis.executor.resultset;

import java.lang.reflect.Constructor;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.loader.ResultLoader;
import org.apache.ibatis.executor.loader.ResultLoaderMap;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.result.DefaultResultContext;
import org.apache.ibatis.executor.result.DefaultResultHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
/**
 * 默认的结果处理器： 封装数据库返回的结果集 ResultSet
 * 
 */
public class DefaultResultSetHandler implements ResultSetHandler {

  private static final Object NO_VALUE = new Object();

  /**
   *  当前 sqlSession 的执行器
   * */
  private final Executor executor;

  /**
   *  mybatis 的全局配置类
   * */
  private final Configuration configuration;

  /**
   *  当前 sql 语句对应的 MappedStatement 对象
   * */
  private final MappedStatement mappedStatement;

  /**
   *  分页参数对象: 由方法参数指定
   * */
  private final RowBounds rowBounds;

  /**
   *  参数处理器
   * */
  private final ParameterHandler parameterHandler;

  /**
   *  结果处理器：由方法参数指定
   * */
  private final ResultHandler resultHandler;

  /**
   *  绑定sql 对象：当前正在执行的sql语句
   * */
  private final BoundSql boundSql;

  /**
   *  类型处理器注册器
   * */
  private final TypeHandlerRegistry typeHandlerRegistry;

  /**
   *  对象工厂： 用于初始化对象
   * */
  private final ObjectFactory objectFactory;

  /**
   *  key: CombinedKey， value: 对应的结果对象
   * */
  private final Map<CacheKey, Object> nestedResultObjects = new HashMap<CacheKey, Object>();

  /**
   *  key: absoluteKey， value: 对应的结果对象
   * */
  private final Map<CacheKey, Object> ancestorObjects = new HashMap<CacheKey, Object>();

  /**
   *  key：ResultMap id, value: 对应的列名前缀
   * */
  private final Map<String, String> ancestorColumnPrefix = new HashMap<String, String>();

  /**
   *  key: 映射指定的结果集， value： ResultMapping 映射
   * */
  private final Map<String, ResultMapping> nextResultMaps = new HashMap<String, ResultMapping>();

  /**
   *  key: 由 ResultMapping、外键列和匹配列的值构建的缓存 Key, value: 待处理的(外键映射)关系
   * */
  private final Map<CacheKey, List<PendingRelation>> pendingRelations = new HashMap<CacheKey, List<PendingRelation>>();

  /**
   *   挂起的关联： 用来保存多结果集的关联关系
   * */
  private static class PendingRelation {
    /**
     *  父类型对应的元对象
     * */
    public MetaObject metaObject;

    /**
     *  通过 columns 和 foreigncolumns 指定的关联映射
     * */
    public ResultMapping propertyMapping;
  }
  
  public DefaultResultSetHandler(Executor executor, MappedStatement mappedStatement, ParameterHandler parameterHandler, ResultHandler resultHandler, BoundSql boundSql,
      RowBounds rowBounds) {
    this.executor = executor;
    this.configuration = mappedStatement.getConfiguration();
    this.mappedStatement = mappedStatement;
    this.rowBounds = rowBounds;
    this.parameterHandler = parameterHandler;
    this.boundSql = boundSql;
    this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
    this.objectFactory = configuration.getObjectFactory();
    this.resultHandler = resultHandler;
  }


  @Override
  public void handleOutputParameters(CallableStatement cs) throws SQLException {
    final Object parameterObject = parameterHandler.getParameterObject();
    final MetaObject metaParam = configuration.newMetaObject(parameterObject);
    final List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    for (int i = 0; i < parameterMappings.size(); i++) {
      final ParameterMapping parameterMapping = parameterMappings.get(i);
      if (parameterMapping.getMode() == ParameterMode.OUT || parameterMapping.getMode() == ParameterMode.INOUT) {
        if (ResultSet.class.equals(parameterMapping.getJavaType())) {
          handleRefCursorOutputParameter((ResultSet) cs.getObject(i + 1), parameterMapping, metaParam);
        } else {
          final TypeHandler<?> typeHandler = parameterMapping.getTypeHandler();
          metaParam.setValue(parameterMapping.getProperty(), typeHandler.getResult(cs, i + 1));
        }
      }
    }
  }

  private void handleRefCursorOutputParameter(ResultSet rs, ParameterMapping parameterMapping, MetaObject metaParam) throws SQLException {
    try {
      final String resultMapId = parameterMapping.getResultMapId();
      final ResultMap resultMap = configuration.getResultMap(resultMapId);
      final DefaultResultHandler resultHandler = new DefaultResultHandler(objectFactory);
      final ResultSetWrapper rsw = new ResultSetWrapper(rs, configuration);
      handleRowValues(rsw, resultMap, resultHandler, new RowBounds(), null);
      metaParam.setValue(parameterMapping.getProperty(), resultHandler.getResultList());
    } finally {
      // issue #228 (close resultsets)
      closeResultSet(rs);
    }
  }

  /**
  *   通过statment获取resultSet,封装成对象返回。
  * */
  @Override
  public List<Object> handleResultSets(Statement stmt) throws SQLException {
    ErrorContext.instance().activity("handling results").object(mappedStatement.getId());
    // 构建集合，用来保存最终结果
    final List<Object> multipleResults = new ArrayList<Object>();

    int resultSetCount = 0;

    //1. 获取 stmt 的执行结果
    ResultSetWrapper rsw = getFirstResultSet(stmt);
    //2. 获取该语句指定的所有 ResultMap
    List<ResultMap> resultMaps = mappedStatement.getResultMaps();
    int resultMapCount = resultMaps.size();
    validateResultMapsCount(rsw, resultMapCount);
    //3. 处理结果集
    while (rsw != null && resultMapCount > resultSetCount) {
      //3.1 首先获取 ResultMap对象
      ResultMap resultMap = resultMaps.get(resultSetCount);
      //3.2 处理结果集（主要逻辑）
      handleResultSet(rsw, resultMap, multipleResults, null);

      rsw = getNextResultSet(stmt);
      cleanUpAfterHandlingResultSet();
      resultSetCount++;
    }

    // 默认情况下，结果映射的所有字段的值都是获取第一个结果集对应的数据， 如果返回了多个结果集，你可以在 resultsets 中指定多个结果集名称， 然后针对某个ResultMapping映射，单独指定结果集
    //获取对应的值，对应的关联关系为 父类型的 column 指定的列， 和当前 ResultMapping 映射 foreignColumn 指定的列相等
    String[] resultSets = mappedStatement.getResulSets();
    if (resultSets != null) {
      while (rsw != null && resultSetCount < resultSets.length) {
        ResultMapping parentMapping = nextResultMaps.get(resultSets[resultSetCount]);
        if (parentMapping != null) {
          String nestedResultMapId = parentMapping.getNestedResultMapId();
          ResultMap resultMap = configuration.getResultMap(nestedResultMapId);
          // 处理该结果集的过程与 3.2 相同，都是解析每一条记录构建该 ResultMap 的结果对象，不同的是，在保存结果对象时，如果该结果对象的外键列和父对象的匹配列的值相同时，将会使用该结果对象，作为父对象的属性值
          handleResultSet(rsw, resultMap, null, parentMapping);
        }
        rsw = getNextResultSet(stmt);
        cleanUpAfterHandlingResultSet();
        resultSetCount++;
      }
    }

    return collapseSingleResultList(multipleResults);
  }

  /**
   *  获取 执行stmt 语句返回的结果集的结果集包装器
   * */
  private ResultSetWrapper getFirstResultSet(Statement stmt) throws SQLException {
    ResultSet rs = stmt.getResultSet();
    //HSQLDB2.1特殊情况处理
    while (rs == null) {
      // move forward to get the first resultset in case the driver
      // doesn't return the resultset as the first result (HSQLDB 2.1)
      if (stmt.getMoreResults()) {
        rs = stmt.getResultSet();
      } else {
        if (stmt.getUpdateCount() == -1) {
          // no more results. Must be no resultset
          break;
        }
      }
    }
    return rs != null ? new ResultSetWrapper(rs, configuration) : null;
  }

  /**
   *  如果当前数据库支持多个结果集，则获取下一个结果集，封装成 ResultSetWrapper 返回
   * */
  private ResultSetWrapper getNextResultSet(Statement stmt) throws SQLException {
    // Making this method tolerant of bad JDBC drivers
    try {
      if (stmt.getConnection().getMetaData().supportsMultipleResultSets()) {
        // Crazy Standard JDBC way of determining if there are more results
        if (!((!stmt.getMoreResults()) && (stmt.getUpdateCount() == -1))) {
          ResultSet rs = stmt.getResultSet();
          return rs != null ? new ResultSetWrapper(rs, configuration) : null;
        }
      }
    } catch (Exception e) {
      // Intentionally ignored.
    }
    return null;
  }

  //关闭结果集
  private void closeResultSet(ResultSet rs) {
    try {
      if (rs != null) {
        rs.close();
      }
    } catch (SQLException e) {
      // ignore
    }
  }


  private void cleanUpAfterHandlingResultSet() {
    nestedResultObjects.clear();
    ancestorColumnPrefix.clear();
  }

  /**
   *  验证是否为当前的结果集指定 ResultMap 或者 ResultType
   * */
  private void validateResultMapsCount(ResultSetWrapper rsw, int resultMapCount) {
    if (rsw != null && resultMapCount < 1) {
      throw new ExecutorException("A query was run and no Result Maps were found for the Mapped Statement '" + mappedStatement.getId()
          + "'.  It's likely that neither a Result Type nor a Result Map was specified.");
    }
  }

  /**
   *  处理结果集的主要逻辑
   * */
  private void handleResultSet(ResultSetWrapper rsw, ResultMap resultMap, List<Object> multipleResults, ResultMapping parentMapping) throws SQLException {
    try {
      //1. 处理关联的多结果集
      if (parentMapping != null) {
        handleRowValues(rsw, resultMap, null, RowBounds.DEFAULT, parentMapping);
      //2. 处理单个结果集
      } else {
        //2.1 如果方法参数没有指定结果处理器
        if (resultHandler == null) {
          //2.1.1 构建默认的结果处理器
          DefaultResultHandler defaultResultHandler = new DefaultResultHandler(objectFactory);
          //2.2.2 处理 ResultSet 结果集对象
          handleRowValues(rsw, resultMap, defaultResultHandler, rowBounds, null);
          //2.2.3 将处理结果保存在 multipleResults 集合中
          multipleResults.add(defaultResultHandler.getResultList());
        //2.2 如果指定了结果处理器
        } else {
          handleRowValues(rsw, resultMap, resultHandler, rowBounds, null);
        }
      }
    } finally {
      //3. 关闭结果集
      // issue #228 (close resultsets)
      closeResultSet(rsw.getResultSet());
    }
  }

  @SuppressWarnings("unchecked")
  private List<Object> collapseSingleResultList(List<Object> multipleResults) {
    return multipleResults.size() == 1 ? (List<Object>) multipleResults.get(0) : multipleResults;
  }

  /*
  *  处理结果集对象，由结果处理器处理处理结果
  * */
  private void handleRowValues(ResultSetWrapper rsw, ResultMap resultMap, ResultHandler resultHandler, RowBounds rowBounds, ResultMapping parentMapping) throws SQLException {

    //1. 如果该 ResultMap 包括内嵌结果集
    if (resultMap.hasNestedResultMaps()) {
      ensureNoRowBounds();
      checkResultHandler();
      handleRowValuesForNestedResultMap(rsw, resultMap, resultHandler, rowBounds, parentMapping);
    //2. 不包含内嵌结果集，直接处理
    } else {
      handleRowValuesForSimpleResultMap(rsw, resultMap, resultHandler, rowBounds, parentMapping);
    }
  }  

  private void ensureNoRowBounds() {
    if (configuration.isSafeRowBoundsEnabled() && rowBounds != null && (rowBounds.getLimit() < RowBounds.NO_ROW_LIMIT || rowBounds.getOffset() > RowBounds.NO_ROW_OFFSET)) {
      throw new ExecutorException("Mapped Statements with nested result mappings cannot be safely constrained by RowBounds. "
          + "Use safeRowBoundsEnabled=false setting to bypass this check.");
    }
  }

  protected void checkResultHandler() {
    if (resultHandler != null && configuration.isSafeResultHandlerEnabled() && !mappedStatement.isResultOrdered()) {
      throw new ExecutorException("Mapped Statements with nested result mappings cannot be safely used with a custom ResultHandler. "
          + "Use safeResultHandlerEnabled=false setting to bypass this check " 
          + "or ensure your statement returns ordered data and set resultOrdered=true on it.");
    }
  } 

  /**
   * 处理简单ResultMap，没有内嵌的resultMap | assocation | case | collection等
   * */
  private void handleRowValuesForSimpleResultMap(ResultSetWrapper rsw, ResultMap resultMap, ResultHandler resultHandler, RowBounds rowBounds, ResultMapping parentMapping)
      throws SQLException {
    //1. 构建默认的结果上下文，用于临时保存处理结果
    DefaultResultContext resultContext = new DefaultResultContext();
    //2. 根据分页对象，使得结果集的光标跳到指定的位置
    skipRows(rsw.getResultSet(), rowBounds);
    //3. 如果还应该处理更多的行且结果集的光标之后还有数据，则通过循环处理
    while (shouldProcessMoreRows(resultContext, rowBounds) && rsw.getResultSet().next()) {
      //3.1 根据鉴别器指定的列名和结果集选择合适的结果映射对象
      ResultMap discriminatedResultMap = resolveDiscriminatedResultMap(rsw.getResultSet(), resultMap, null);
      //3.2 获取该列的处理结果
      Object rowValue = getRowValue(rsw, discriminatedResultMap);
      //3.3 保存（或者处理）结果
      storeObject(resultHandler, resultContext, rowValue, parentMapping, rsw.getResultSet());
    }
  }

  /**
   * @param rowValue 结果集一行记录对应的java对象
   * */
  private void storeObject(ResultHandler resultHandler, DefaultResultContext resultContext, Object rowValue, ResultMapping parentMapping, ResultSet rs) throws SQLException {
    //1. 如果为处理关联多结果集
    if (parentMapping != null) {
      linkToParents(rs, parentMapping, rowValue);
    //2. 通过结果处理器进行处理
    } else {
      callResultHandler(resultHandler, resultContext, rowValue);
    }
  }

  /**
   *  由结果处理器处理结果、结果上下文存储结果
   * */
  private void callResultHandler(ResultHandler resultHandler, DefaultResultContext resultContext, Object rowValue) {
    resultContext.nextResultObject(rowValue);
    resultHandler.handleResult(resultContext);
  }


  /**
   * 判断结果上下文保存的处理结果是否小于分页的limit，如果不小于，则说明已经取得的结果已经够了，返回false
   * */
  private boolean shouldProcessMoreRows(ResultContext context, RowBounds rowBounds) throws SQLException {
    return !context.isStopped() && context.getResultCount() < rowBounds.getLimit();
  }

  /**
   *  如果参数带有 RowBounds 类型的对象，则根据该对象，跳过偏移量之前的行，移动光标到偏移量的位置
   * */
  private void skipRows(ResultSet rs, RowBounds rowBounds) throws SQLException {
    if (rs.getType() != ResultSet.TYPE_FORWARD_ONLY) {
      if (rowBounds.getOffset() != RowBounds.NO_ROW_OFFSET) {
        rs.absolute(rowBounds.getOffset());
      }
    } else {
      for (int i = 0; i < rowBounds.getOffset(); i++) {
        rs.next();
      }
    }
  }



  /**
   *   将结果集中的一行记录转化为 java对象
   * */
  private Object getRowValue(ResultSetWrapper rsw, ResultMap resultMap) throws SQLException {
    //1. 初始化延迟加载器
    final ResultLoaderMap lazyLoader = new ResultLoaderMap();
    //2. 初始化结果对象
    Object resultObject = createResultObject(rsw, resultMap, lazyLoader, null);
    //3. 如果复杂对象，一般复杂类型是没有类型处理器的，除非我们自定义
    if (resultObject != null && !typeHandlerRegistry.hasTypeHandler(resultMap.getType())) {
      final MetaObject metaObject = configuration.newMetaObject(resultObject);
      boolean foundValues = !resultMap.getConstructorResultMappings().isEmpty();
      //3.1 如果指定了自动映射，则进行自动映射
      if (shouldApplyAutomaticMappings(resultMap, false)) {        
    	foundValues = applyAutomaticMappings(rsw, resultMap, metaObject, null) || foundValues;
      }
      //3.2 对在 ResultMap 中指定手动映射的列赋值到结果对象的字段中
      foundValues = applyPropertyMappings(rsw, resultMap, metaObject, lazyLoader, null) || foundValues;
      foundValues = lazyLoader.size() > 0 || foundValues;
      resultObject = foundValues ? resultObject : null;
      return resultObject;
    }
    return resultObject;
  }

  /**
   *  判断当前对当前的ResultMap指定的java类型是否应该进行自动映射
   * */
  private boolean shouldApplyAutomaticMappings(ResultMap resultMap, boolean isNested) {
    if (resultMap.getAutoMapping() != null) {
      return resultMap.getAutoMapping();
    } else {
      if (isNested) {
        return AutoMappingBehavior.FULL == configuration.getAutoMappingBehavior();
      } else {
        return AutoMappingBehavior.NONE != configuration.getAutoMappingBehavior();
      }
    }
  }

  /**
   *  ResultMap 手动指定的映射为字段赋值
   * */
  private boolean applyPropertyMappings(ResultSetWrapper rsw, ResultMap resultMap, MetaObject metaObject, ResultLoaderMap lazyLoader, String columnPrefix)
      throws SQLException {
    final List<String> mappedColumnNames = rsw.getMappedColumnNames(resultMap, columnPrefix);
    boolean foundValues = false;
    final List<ResultMapping> propertyMappings = resultMap.getPropertyResultMappings();
    // 遍历该 ResultMap 下的所有映射
    for (ResultMapping propertyMapping : propertyMappings) {
      final String column = prependPrefix(propertyMapping.getColumn(), columnPrefix);
      //1. 如果为内嵌查询 | 如果指定映射的列名包括当前列名 | 当前映射指定了结果集名（多结果集）
      if (propertyMapping.isCompositeResult() 
          || (column != null && mappedColumnNames.contains(column.toUpperCase(Locale.ENGLISH))) 
          || propertyMapping.getResultSet() != null) {
        //2. 获取列名对应的值，然后通过元对象为字段赋值
        Object value = getPropertyMappingValue(rsw.getResultSet(), metaObject, propertyMapping, lazyLoader, columnPrefix);
        // issue #541 make property optional
        final String property = propertyMapping.getProperty();
        // issue #377, call setter on nulls
        if (value != NO_VALUE && property != null && (value != null || configuration.isCallSettersOnNulls())) {
          if (value != null || !metaObject.getSetterType(property).isPrimitive()) {
            metaObject.setValue(property, value);
          }
          foundValues = true;
        }
      }
    }
    return foundValues;
  }

  /**
   * 获取列名对应的值
   * */
  private Object getPropertyMappingValue(ResultSet rs, MetaObject metaResultObject, ResultMapping propertyMapping, ResultLoaderMap lazyLoader, String columnPrefix)
      throws SQLException {
    //1. 如果包含内查询，则获取查询结果作为当前字段的值
    if (propertyMapping.getNestedQueryId() != null) {
      return getNestedQueryMappingValue(rs, metaResultObject, propertyMapping, lazyLoader, columnPrefix);
    //2. 如果该映射指定了结果集，则将该映射以及所处的结果对象保存到 map 中
    } else if (propertyMapping.getResultSet() != null) {
      addPendingChildRelation(rs, metaResultObject, propertyMapping);
      return NO_VALUE;
    //3. 如果字段映射包含结果映射 ResultMap，则先返回空
    } else if (propertyMapping.getNestedResultMapId() != null) {
      // the user added a column attribute to a nested result map, ignore it
      return NO_VALUE;
    //4. 直接通过类型处理器获取映射指定的列对应的值
    } else {
      final TypeHandler<?> typeHandler = propertyMapping.getTypeHandler();
      final String column = prependPrefix(propertyMapping.getColumn(), columnPrefix);
      return typeHandler.getResult(rs, column);
    }
  }

  /**
   *  自动映射： 将未被指定映射的列名的值赋值到结果对象与列名相同的字段上
   * */
  private boolean applyAutomaticMappings(ResultSetWrapper rsw, ResultMap resultMap, MetaObject metaObject, String columnPrefix) throws SQLException {
    final List<String> unmappedColumnNames = rsw.getUnmappedColumnNames(resultMap, columnPrefix);
    boolean foundValues = false;
    // 遍历所有未指定映射的列名
    for (String columnName : unmappedColumnNames) {
      //1. 获取列名对应的属性名（去掉前缀）
      String propertyName = columnName;
      if (columnPrefix != null && !columnPrefix.isEmpty()) {
        if (columnName.toUpperCase(Locale.ENGLISH).startsWith(columnPrefix)) {
          propertyName = columnName.substring(columnPrefix.length());
        } else {
          continue;
        }
      }
      //2. 如果结果对象中存在该属性名的 set 调用者，则进行赋值
      final String property = metaObject.findProperty(propertyName, configuration.isMapUnderscoreToCamelCase());
      if (property != null && metaObject.hasSetter(property)) {
        final Class<?> propertyType = metaObject.getSetterType(property);
        if (typeHandlerRegistry.hasTypeHandler(propertyType)) {
          final TypeHandler<?> typeHandler = rsw.getTypeHandler(propertyType, columnName);
          final Object value = typeHandler.getResult(rsw.getResultSet(), columnName);
          // issue #377, call setter on nulls
          if (value != null || configuration.isCallSettersOnNulls()) {
            if (value != null || !propertyType.isPrimitive()) {
              metaObject.setValue(property, value);
            }
            foundValues = true;
          }
        }
      }
    }
    return foundValues;
  }

  // MULTIPLE RESULT SETS

  /**
   * @param rs 用来获取数据的结果集对象
   * @param parentMapping 指定结果集的 ResultMapping 对象
   * @param rowValue 获取关联结果集的解析结果
   * */
  private void linkToParents(ResultSet rs, ResultMapping parentMapping, Object rowValue) throws SQLException {
    //1. 通过 指定的foreignColumn列的值构建缓存 key
    CacheKey parentKey = createKeyForMultipleResults(rs, parentMapping, parentMapping.getColumn(), parentMapping.getForeignColumn());
    //2. 如果 pendingRelations 中存在对应的value, 则说明 外键列和匹配列的值相等
    List<PendingRelation> parents = pendingRelations.get(parentKey);
    //3. 进行重新赋值，将指定的关联结果集的映射结果，设置到父对象的属性中
    for (PendingRelation parent : parents) {
      if (parent != null) {
        final Object collectionProperty = instantiateCollectionPropertyIfAppropriate(parent.propertyMapping, parent.metaObject);
        if (rowValue != null) {
          if (collectionProperty != null) {
            final MetaObject targetMetaObject = configuration.newMetaObject(collectionProperty);
            targetMetaObject.add(rowValue);
          } else {
            parent.metaObject.setValue(parent.propertyMapping.getProperty(), rowValue);
          }
        }
      }
    }
  }

  /**
   *  如果该 ResultMapping 中指定的 属性为 null且是集合时，初始化集合，否则直接返回对应的值
   * */
  private Object instantiateCollectionPropertyIfAppropriate(ResultMapping resultMapping, MetaObject metaObject) {
    final String propertyName = resultMapping.getProperty();
    Object propertyValue = metaObject.getValue(propertyName);
    if (propertyValue == null) {
      Class<?> type = resultMapping.getJavaType();
      if (type == null) {
        type = metaObject.getSetterType(propertyName);
      }
      try {
        if (objectFactory.isCollection(type)) {
          propertyValue = objectFactory.create(type);
          metaObject.setValue(propertyName, propertyValue);
          return propertyValue;
        }
      } catch (Exception e) {
        throw new ExecutorException("Error instantiating collection property for result '" + resultMapping.getProperty() + "'.  Cause: " + e, e);
      }
    } else if (objectFactory.isCollection(propertyValue.getClass())) {
      return propertyValue;
    }
    return null;
  }

  /**
   * @param rs 正在解析的结果映射
   * @param metaResultObject 当前结果映射对应的结果对象
   * @param parentMapping 正在解析的 字段 == 属性 的映射
   * */
  private void addPendingChildRelation(ResultSet rs, MetaObject metaResultObject, ResultMapping parentMapping) throws SQLException {
    //1. 通过 ResultMapping对象、 外键列和匹配列的值构建缓存 key
    CacheKey cacheKey = createKeyForMultipleResults(rs, parentMapping, parentMapping.getColumn(), parentMapping.getColumn());
    //2. 通过 PendingRelation 对象保存 映射关系
    PendingRelation deferLoad = new PendingRelation();
    deferLoad.metaObject = metaResultObject;
    deferLoad.propertyMapping = parentMapping;

    List<PendingRelation> relations = pendingRelations.get(cacheKey);
    // issue #255
    if (relations == null) {
      relations = new ArrayList<DefaultResultSetHandler.PendingRelation>();
      pendingRelations.put(cacheKey, relations);
    }
    relations.add(deferLoad);
    //3. 一个结果集最多只能被一个映射所使用
    ResultMapping previous = nextResultMaps.get(parentMapping.getResultSet());
    if (previous == null) {
      nextResultMaps.put(parentMapping.getResultSet(), parentMapping);
    } else {
      if (!previous.equals(parentMapping)) {
        throw new ExecutorException("Two different properties are mapped to the same resultSet");
      }
    }
  }

  /**
   *  为多结果集构建缓存key
   * @param rs 当前的 sql 语句执行的结果集
   * @param resultMapping 当前 ResultMap中 指定结果集的 属性映射
   * @param names 如果指定了多结果集，用于和外键列匹配的列名
   * @param columns 外键列，用于与父类型中 column 的给出列的进行匹配
   * */
  private CacheKey createKeyForMultipleResults(ResultSet rs, ResultMapping resultMapping, String names, String columns) throws SQLException {
    CacheKey cacheKey = new CacheKey();
    cacheKey.update(resultMapping);
    if (columns != null && names != null) {
      String[] columnsArray = columns.split(",");
      String[] namesArray = names.split(",");
      for (int i = 0 ; i < columnsArray.length ; i++) {
        Object value = rs.getString(columnsArray[i]);
        if (value != null) {
          cacheKey.update(namesArray[i]);
          cacheKey.update(value);
        }
      }
    }
    return cacheKey;
  }


  /**
   *   创建结果对象（初始化）
   * */
  private Object createResultObject(ResultSetWrapper rsw, ResultMap resultMap, ResultLoaderMap lazyLoader, String columnPrefix) throws SQLException {
    final List<Class<?>> constructorArgTypes = new ArrayList<Class<?>>();
    final List<Object> constructorArgs = new ArrayList<Object>();
    //1. 通过对象工厂初始化对象
    final Object resultObject = createResultObject(rsw, resultMap, constructorArgTypes, constructorArgs, columnPrefix);
    //2. 如果指定了某一列为延迟加载，则根据对象构建动态代理对象
    if (resultObject != null && !typeHandlerRegistry.hasTypeHandler(resultMap.getType())) {
      final List<ResultMapping> propertyMappings = resultMap.getPropertyResultMappings();
      for (ResultMapping propertyMapping : propertyMappings) {
        // issue gcode #109 && issue #149
        if (propertyMapping.getNestedQueryId() != null && propertyMapping.isLazy()) {
          //使用代理(cglib/javaassist) todo lisilu 延迟加载代理
          return configuration.getProxyFactory().createProxy(resultObject, lazyLoader, configuration, objectFactory, constructorArgTypes, constructorArgs);
        }
      }
    }
    return resultObject;
  }

  /**
   *  通过 ResultMap 指定的类型通过对象工厂初始化对象
   * */
  private Object createResultObject(ResultSetWrapper rsw, ResultMap resultMap, List<Class<?>> constructorArgTypes, List<Object> constructorArgs, String columnPrefix)
      throws SQLException {
    //1. 获取 ResultMap 指定的对象类型
    final Class<?> resultType = resultMap.getType();
    final MetaClass metaType = MetaClass.forClass(resultType);
    //2. 获取该  ResultMap 下所有的构造器映射
    final List<ResultMapping> constructorMappings = resultMap.getConstructorResultMappings();
    //3. 如果该ResultMap为基本类型，则直接获取结果集中的结果返回
    if (typeHandlerRegistry.hasTypeHandler(resultType)) {
      return createPrimitiveResultObject(rsw, resultMap, columnPrefix);
    //4. 如果指定了构造器映射，则使用对应的构造器初始化对象
    } else if (!constructorMappings.isEmpty()) {
      return createParameterizedResultObject(rsw, resultType, constructorMappings, constructorArgTypes, constructorArgs, columnPrefix);
    //5. 如果有默认构造器，则使用默认构造器初始化对象
    } else if (resultType.isInterface() || metaType.hasDefaultConstructor()) {
      return objectFactory.create(resultType);
    //6. 如果当前的结果对象的映射规则为自动映射
    } else if (shouldApplyAutomaticMappings(resultMap, false)) {
      return createByConstructorSignature(rsw, resultType, constructorArgTypes, constructorArgs, columnPrefix);
    }
    throw new ExecutorException("Do not know how to create an instance of " + resultType);
  }

  /**
   * 如果该 ResultMap 指定了构造映射，则根据构造映射获取 ResultMap 指定的 java类型对应的构造器，通过对象工厂初始化对象
   * */
  private Object createParameterizedResultObject(ResultSetWrapper rsw, Class<?> resultType, List<ResultMapping> constructorMappings,
      List<Class<?>> constructorArgTypes, List<Object> constructorArgs, String columnPrefix) throws SQLException {
    boolean foundValues = false;
    // 遍历所有的构造器映射
    for (ResultMapping constructorMapping : constructorMappings) {
      final Class<?> parameterType = constructorMapping.getJavaType();
      final String column = constructorMapping.getColumn();
      final Object value;
      //1. 如果有内嵌查询( seletct) 则获取查询结果作为当前参数的值
      if (constructorMapping.getNestedQueryId() != null) {
        value = getNestedQueryConstructorValue(rsw.getResultSet(), constructorMapping, columnPrefix);
      //2. 如果当前的有嵌套的复杂 resultMap | case | assocatoin | collection等, 则通过结果集以及结果映射获取结果作为参数的值
      } else if (constructorMapping.getNestedResultMapId() != null) {
        final ResultMap resultMap = configuration.getResultMap(constructorMapping.getNestedResultMapId());
        value = getRowValue(rsw, resultMap);
      //3. 否则，则直接通过类型处理器从结果集中获取指定列名对应的值作为参数的值
      } else {
        final TypeHandler<?> typeHandler = constructorMapping.getTypeHandler();
        value = typeHandler.getResult(rsw.getResultSet(), prependPrefix(column, columnPrefix));
      }
      constructorArgTypes.add(parameterType);
      constructorArgs.add(value);
      foundValues = value != null || foundValues;
    }
    return foundValues ? objectFactory.create(resultType, constructorArgTypes, constructorArgs) : null;
  }

  /**
   *  通过自动映射，获取结果对象 ResultType 的构造函数的各个参数对应的值，通过对象工厂进行创建
   * */
  private Object createByConstructorSignature(ResultSetWrapper rsw, Class<?> resultType, List<Class<?>> constructorArgTypes, List<Object> constructorArgs,
      String columnPrefix) throws SQLException {
    // 遍历所有的构造函数，选择合适的构造函数进行初始化
    for (Constructor<?> constructor : resultType.getDeclaredConstructors()) {
       // 如果构造函数的参数类型和结果集返回的结果类型匹配
      if (typeNames(constructor.getParameterTypes()).equals(rsw.getClassNames())) {
        boolean foundValues = false;
        // 使用类型处理器获取构造映射指定的列名对应的值，作为构造参数
        for (int i = 0; i < constructor.getParameterTypes().length; i++) {
          Class<?> parameterType = constructor.getParameterTypes()[i];
          String columnName = rsw.getColumnNames().get(i);
          TypeHandler<?> typeHandler = rsw.getTypeHandler(parameterType, columnName);
          Object value = typeHandler.getResult(rsw.getResultSet(), prependPrefix(columnName, columnPrefix));
          constructorArgTypes.add(parameterType);
          constructorArgs.add(value);
          foundValues = value != null || foundValues;
        }
        //上面是构造函数创建对象，下面是对象工厂来创建
        return foundValues ? objectFactory.create(resultType, constructorArgTypes, constructorArgs) : null;
      }
    }
    throw new ExecutorException("No constructor found in " + resultType.getName() + " matching " + rsw.getClassNames());
  }

  /**
   *  将类型数组转化为对应的类的简单名称的字符集合
   * */
  private List<String> typeNames(Class<?>[] parameterTypes) {
    List<String> names = new ArrayList<String>();
    for (Class<?> type : parameterTypes) {
      names.add(type.getName());
    }
    return names;
  }

  /**
   *  如果 ResultMap 指定的类型为基本类型，则直接获取结果集返回的结果返回
   * */
  private Object createPrimitiveResultObject(ResultSetWrapper rsw, ResultMap resultMap, String columnPrefix) throws SQLException {
    final Class<?> resultType = resultMap.getType();
    final String columnName;
    if (!resultMap.getResultMappings().isEmpty()) {
      final List<ResultMapping> resultMappingList = resultMap.getResultMappings();
      final ResultMapping mapping = resultMappingList.get(0);
      columnName = prependPrefix(mapping.getColumn(), columnPrefix);
    } else {
      columnName = rsw.getColumnNames().get(0);
    }
    final TypeHandler<?> typeHandler = rsw.getTypeHandler(resultType, columnName);
    return typeHandler.getResult(rsw.getResultSet(), columnName);
  }


  /**
   *  如果结果映射指定了 select，则获取该查询结果作为当前参数的值
   * */
  private Object getNestedQueryConstructorValue(ResultSet rs, ResultMapping constructorMapping, String columnPrefix) throws SQLException {
    //1. 获取内嵌查询的 MappedStatement 对象
    final String nestedQueryId = constructorMapping.getNestedQueryId();
    final MappedStatement nestedQuery = configuration.getMappedStatement(nestedQueryId);
    //2. 获取内嵌查询所需要的参数类型
    final Class<?> nestedQueryParameterType = nestedQuery.getParameterMap().getType();
    //3. 获取内嵌查询所有的参数对象
    final Object nestedQueryParameterObject = prepareParameterForNestedQuery(rs, constructorMapping, nestedQueryParameterType, columnPrefix);
    Object value = null;
    if (nestedQueryParameterObject != null) {
      //4. 准备 sql、缓存key、返回值类型等等
      final BoundSql nestedBoundSql = nestedQuery.getBoundSql(nestedQueryParameterObject);
      final CacheKey key = executor.createCacheKey(nestedQuery, nestedQueryParameterObject, RowBounds.DEFAULT, nestedBoundSql);
      final Class<?> targetType = constructorMapping.getJavaType();
      //5. 构建结果加载器，加载结果
      final ResultLoader resultLoader = new ResultLoader(configuration, executor, nestedQuery, nestedQueryParameterObject, targetType, key, nestedBoundSql);
      value = resultLoader.loadResult();
    }
    //6. 返回内嵌查询的结果
    return value;
  }


  /**
   *  获取内嵌查询的结果
   * */
  private Object getNestedQueryMappingValue(ResultSet rs, MetaObject metaResultObject, ResultMapping propertyMapping, ResultLoaderMap lazyLoader, String columnPrefix)
      throws SQLException {
    //1. 获取内嵌查询对应的 MappedStatement
    final String nestedQueryId = propertyMapping.getNestedQueryId();
    final String property = propertyMapping.getProperty();
    final MappedStatement nestedQuery = configuration.getMappedStatement(nestedQueryId);
    final Class<?> nestedQueryParameterType = nestedQuery.getParameterMap().getType();
    //2. 为内嵌查询准备参数
    final Object nestedQueryParameterObject = prepareParameterForNestedQuery(rs, propertyMapping, nestedQueryParameterType, columnPrefix);
    Object value = NO_VALUE;
    if (nestedQueryParameterObject != null) {
      final BoundSql nestedBoundSql = nestedQuery.getBoundSql(nestedQueryParameterObject);
      final CacheKey key = executor.createCacheKey(nestedQuery, nestedQueryParameterObject, RowBounds.DEFAULT, nestedBoundSql);
      final Class<?> targetType = propertyMapping.getJavaType();
      //3. 如果该查询已经被缓存
      if (executor.isCached(nestedQuery, key)) {
        executor.deferLoad(nestedQuery, metaResultObject, property, key, targetType);
      //4. 否则，构建结果加载器加载对应的结果
      } else {
        final ResultLoader resultLoader = new ResultLoader(configuration, executor, nestedQuery, nestedQueryParameterObject, targetType, key, nestedBoundSql);
        if (propertyMapping.isLazy()) {
          lazyLoader.addLoader(property, metaResultObject, resultLoader);
        } else {
          value = resultLoader.loadResult();
        }
      }
    }
    return value;
  }

  /**
   *  为内嵌查询准备参数，返回内嵌查询需要的参数
   * */
  private Object prepareParameterForNestedQuery(ResultSet rs, ResultMapping resultMapping, Class<?> parameterType, String columnPrefix) throws SQLException {
    //1. 如果为复合列
    if (resultMapping.isCompositeResult()) {
      return prepareCompositeKeyParameter(rs, resultMapping, parameterType, columnPrefix);
    //2. 如果为简单列（最多传入了一个参数）
    } else {
      return prepareSimpleKeyParameter(rs, resultMapping, parameterType, columnPrefix);
    }
  }

  /**
   *  通过类型处理器获取 指定列的值
   * */
  private Object prepareSimpleKeyParameter(ResultSet rs, ResultMapping resultMapping, Class<?> parameterType, String columnPrefix) throws SQLException {
    final TypeHandler<?> typeHandler;
    if (typeHandlerRegistry.hasTypeHandler(parameterType)) {
      typeHandler = typeHandlerRegistry.getTypeHandler(parameterType);
    } else {
      typeHandler = typeHandlerRegistry.getUnknownTypeHandler();
    }
    return typeHandler.getResult(rs, prependPrefix(resultMapping.getColumn(), columnPrefix));
  }

  /**
   *  如果为复合列对象（向内嵌查询传入了多个列值）
   * */
  private Object prepareCompositeKeyParameter(ResultSet rs, ResultMapping resultMapping, Class<?> parameterType, String columnPrefix) throws SQLException {
    //1. 初始化参数对象
    final Object parameterObject = instantiateParameterObject(parameterType);
    final MetaObject metaObject = configuration.newMetaObject(parameterObject);
    boolean foundValues = false;
    //2. 遍历所有的复合列，根据复合列指定的列名为参数对象对应的属性赋值
    for (ResultMapping innerResultMapping : resultMapping.getComposites()) {
      final Class<?> propType = metaObject.getSetterType(innerResultMapping.getProperty());
      final TypeHandler<?> typeHandler = typeHandlerRegistry.getTypeHandler(propType);
      final Object propValue = typeHandler.getResult(rs, prependPrefix(innerResultMapping.getColumn(), columnPrefix));
      // issue #353 & #560 do not execute nested query if key is null
      if (propValue != null) {
        metaObject.setValue(innerResultMapping.getProperty(), propValue);
        foundValues = true;
      }
    }
    return foundValues ? parameterObject : null;
  }

  /**
   *  根据参数类型初始化对象，默认为 HashMap
   * */
  private Object instantiateParameterObject(Class<?> parameterType) {
    if (parameterType == null) {
      return new HashMap<Object, Object>();
    } else {
      return objectFactory.create(parameterType);
    }
  }

  /**
   *  如果当前结果映射中包含鉴别器，则根据结果集返回的值，使用对应的 映射（列名 <===> 字段名）集合
   * */
  public ResultMap resolveDiscriminatedResultMap(ResultSet rs, ResultMap resultMap, String columnPrefix) throws SQLException {
    Set<String> pastDiscriminators = new HashSet<String>();
    Discriminator discriminator = resultMap.getDiscriminator();
    // 通过循环的方式，处理鉴别器的 case分支 里内嵌的鉴别器
    while (discriminator != null) {
      //1. 根据鉴别器指定的列名，获取对应在结果集里的值
      final Object value = getDiscriminatorValue(rs, discriminator, columnPrefix);
      //2. 根据值，从鉴别器中获取符合的分支对应的唯一标识
      final String discriminatedMapId = discriminator.getMapIdFor(String.valueOf(value));
      //3. 如果全局配置类中包含对应的 ResultMap
      if (configuration.hasResultMap(discriminatedMapId)) {
        //3.1 使用该 Result 暂时作为最终使用的结果映射
        resultMap = configuration.getResultMap(discriminatedMapId);
        Discriminator lastDiscriminator = discriminator;
        //3.2 如果该结果映射中还包含鉴别器，则进行下一次循环
        discriminator = resultMap.getDiscriminator();
        if (discriminator == lastDiscriminator || !pastDiscriminators.add(discriminatedMapId)) {
          break;
        }
      } else {
        break;
      }
    }
    //4. 返回需要使用的 ResultMap 对象
    return resultMap;
  }

  /**
   *  根据鉴别器指定的列名，返回结果集中对应的值
   * */
  private Object getDiscriminatorValue(ResultSet rs, Discriminator discriminator, String columnPrefix) throws SQLException {
    final ResultMapping resultMapping = discriminator.getResultMapping();
    final TypeHandler<?> typeHandler = resultMapping.getTypeHandler();
    return typeHandler.getResult(rs, prependPrefix(resultMapping.getColumn(), columnPrefix));
  }

  private String prependPrefix(String columnName, String prefix) {
    if (columnName == null || columnName.length() == 0 || prefix == null || prefix.length() == 0) {
      return columnName;
    }
    return prefix + columnName;
  }


  /**
   *  处理嵌套的 ResultMap
   * */
  private void handleRowValuesForNestedResultMap(ResultSetWrapper rsw, ResultMap resultMap, ResultHandler resultHandler, RowBounds rowBounds, ResultMapping parentMapping) throws SQLException {
    final DefaultResultContext resultContext = new DefaultResultContext();
    //1. 移动光标到 分页对象 RowBounds 指定的偏移量上
    skipRows(rsw.getResultSet(), rowBounds);
    Object rowValue = null;
    //2. 如果已解析的记录数还没有超过 分页对象指定的页大小，且光标之后还有记录
    while (shouldProcessMoreRows(resultContext, rowBounds) && rsw.getResultSet().next()) {
      final ResultMap discriminatedResultMap = resolveDiscriminatedResultMap(rsw.getResultSet(), resultMap, null);
      final CacheKey rowKey = createRowKey(discriminatedResultMap, rsw, null);
      Object partialObject = nestedResultObjects.get(rowKey);
      // issue #577 && #542 todo lesleey ResultOrdered
      if (mappedStatement.isResultOrdered()) {
        if (partialObject == null && rowValue != null) {
          nestedResultObjects.clear();
          storeObject(resultHandler, resultContext, rowValue, parentMapping, rsw.getResultSet());
        }
        rowValue = getRowValue(rsw, discriminatedResultMap, rowKey, rowKey, null, partialObject);
      //2.2 如果 ResultOrdered 不为 true
      } else {
        //2.2.1 通过 ResultMap 解析该条记录为对应的 java 对象
        rowValue = getRowValue(rsw, discriminatedResultMap, rowKey, rowKey, null, partialObject);
        if (partialObject == null) {
          storeObject(resultHandler, resultContext, rowValue, parentMapping, rsw.getResultSet());
        }
      }
    }
    if (rowValue != null && mappedStatement.isResultOrdered() && shouldProcessMoreRows(resultContext, rowBounds)) {
      storeObject(resultHandler, resultContext, rowValue, parentMapping, rsw.getResultSet());
    }
  }
  
  /**
   *   GET VALUE FROM ROW FOR NESTED RESULT MAP
   * @param resultMap  当前正在处理的结果映射
   * @param combinedKey ResultSet 范围内唯一，一个完整的结果映射类似一棵树，内嵌的 ResultMap 类似树的子节点。 当处理到同一个 节点（ResultMap） 时, 如果该节点以及其所有的父节点所使用的映射的值
   *                    都相同，则生成的 combinedKey 相同（比如 内嵌的 ResultMap<collection />， 对于多条sql记录，保证其所属的类只会解析一次）
   * @param absoluteKey 在解析一条记录的过程中，通过该key可以确定当前 ResultMap 对应的解析结果[java对象]
   * @param partialObject combinedKey 对应的缓存项, 也就是当前ResultMap 的处理结果（如果之前又被处理过）
   * */
  private Object getRowValue(ResultSetWrapper rsw, ResultMap resultMap, CacheKey combinedKey, CacheKey absoluteKey, String columnPrefix, Object partialObject) throws SQLException {

    final String resultMapId = resultMap.getId();
    Object resultObject = partialObject;
    //1. 如果 当前的 ResultMap 在缓存中存在对应的解析结果
    if (resultObject != null) {
      //1.1 直接设置到 所属类的属性中
      final MetaObject metaObject = configuration.newMetaObject(resultObject);
      //1.2 将 absoluteKey 和对应的解析结果添加到 map 对象中
      putAncestor(absoluteKey, resultObject, resultMapId, columnPrefix);
      //1.3 处理内嵌的 ResultMap
      applyNestedResultMappings(rsw, resultMap, metaObject, columnPrefix, combinedKey, false);
      ancestorObjects.remove(absoluteKey);
    //2.
    } else {
      final ResultLoaderMap lazyLoader = new ResultLoaderMap();
      //2.1 初始化当前 ResultMap 对应的结果对象
      resultObject = createResultObject(rsw, resultMap, lazyLoader, columnPrefix);
      if (resultObject != null && !typeHandlerRegistry.hasTypeHandler(resultMap.getType())) {
        final MetaObject metaObject = configuration.newMetaObject(resultObject);
        boolean foundValues = !resultMap.getConstructorResultMappings().isEmpty();
        //2.2 进行自动映射和手动映射处理
        if (shouldApplyAutomaticMappings(resultMap, true)) {
          foundValues = applyAutomaticMappings(rsw, resultMap, metaObject, columnPrefix) || foundValues;
        }        
        foundValues = applyPropertyMappings(rsw, resultMap, metaObject, lazyLoader, columnPrefix) || foundValues;
        //2.3 将 absolutekey 和 对应的结果对象的对应关系存放到 map 中
        putAncestor(absoluteKey, resultObject, resultMapId, columnPrefix);
        //2.4 处理内嵌的 ResultMap
        foundValues = applyNestedResultMappings(rsw, resultMap, metaObject, columnPrefix, combinedKey, true) || foundValues;
        ancestorObjects.remove(absoluteKey);
        foundValues = lazyLoader.size() > 0 || foundValues;
        resultObject = foundValues ? resultObject : null;
      }
      if (combinedKey != CacheKey.NULL_CACHE_KEY) {
        nestedResultObjects.put(combinedKey, resultObject);
      }
    }
    return resultObject;
  }


  private void putAncestor(CacheKey rowKey, Object resultObject, String resultMapId, String columnPrefix) {
    if (!ancestorColumnPrefix.containsKey(resultMapId)) {
      ancestorColumnPrefix.put(resultMapId, columnPrefix);
    }
    ancestorObjects.put(rowKey, resultObject);
  }


  /**
   *  处理 包含内嵌 ResultMap 的ResultMapping
   * */
  private boolean applyNestedResultMappings(ResultSetWrapper rsw, ResultMap resultMap, MetaObject metaObject, String parentPrefix, CacheKey parentRowKey, boolean newObject) {
    boolean foundValues = false;
    // 遍历该 ResultMap 下指定的所有映射
    for (ResultMapping resultMapping : resultMap.getPropertyResultMappings()) {
      final String nestedResultMapId = resultMapping.getNestedResultMapId();
      //  如果该映射内包含内嵌的 ResultMap，则进行以下处理
      if (nestedResultMapId != null && resultMapping.getResultSet() == null) {
        try {
          final String columnPrefix = getColumnPrefix(parentPrefix, resultMapping);
          final ResultMap nestedResultMap = getNestedResultMap(rsw.getResultSet(), nestedResultMapId, columnPrefix);
          CacheKey rowKey = null;
          Object ancestorObject = null;
          //1.1 如果 ancestorColumnPrefix 集合中包含内嵌的 ResultMapId, 说明 在处理这条记录时，该 ResultMap 已经被解析过.
          if (ancestorColumnPrefix.containsKey(nestedResultMapId)) {
            //1.1.2 获取对应的解析结果
            rowKey = createRowKey(nestedResultMap, rsw, ancestorColumnPrefix.get(nestedResultMapId));
            ancestorObject = ancestorObjects.get(rowKey);
          }
          //1.2 如果该 ResultMap 对应的解析结果不为空，则直接将该值设置到所属类对应的属性中
          if (ancestorObject != null) { 
            if (newObject) {
              metaObject.setValue(resultMapping.getProperty(), ancestorObject);
            }
          //1.3
          } else {
            //1.3.1 通过 ResultMap, rsw 构建 absoluteKey
            rowKey = createRowKey(nestedResultMap, rsw, columnPrefix);
            //1.3.2 构建 combineKey
            final CacheKey combinedKey = combineKeys(rowKey, parentRowKey);            
            Object rowValue = nestedResultObjects.get(combinedKey);
            boolean knownValue = (rowValue != null);
            //1.3.3 如果该属性为集合，则进行初始化
            final Object collectionProperty = instantiateCollectionPropertyIfAppropriate(resultMapping, metaObject);
            //1.3.4 如果指定的非空列任一存在值
            if (anyNotNullColumnHasValue(resultMapping, columnPrefix, rsw.getResultSet())) {
              //1.3.4.1 获取解析该内嵌 ResultMap的结果
              rowValue = getRowValue(rsw, nestedResultMap, combinedKey, rowKey, columnPrefix, rowValue);
              //1.3.4.2 如果 combinedKey对应的结果不存在
              if (rowValue != null && !knownValue) {
                if (collectionProperty != null) {
                  final MetaObject targetMetaObject = configuration.newMetaObject(collectionProperty);
                  targetMetaObject.add(rowValue);
                } else {
                  metaObject.setValue(resultMapping.getProperty(), rowValue);
                }
                foundValues = true;
              }
            }
          }
        } catch (SQLException e) {
          throw new ExecutorException("Error getting nested result map values for '" + resultMapping.getProperty() + "'.  Cause: " + e, e);
        }
      }
    }
    return foundValues;
  }

  private String getColumnPrefix(String parentPrefix, ResultMapping resultMapping) {
    final StringBuilder columnPrefixBuilder = new StringBuilder();
    if (parentPrefix != null) {
      columnPrefixBuilder.append(parentPrefix);
    }
    if (resultMapping.getColumnPrefix() != null) {
      columnPrefixBuilder.append(resultMapping.getColumnPrefix());
    }
    return columnPrefixBuilder.length() == 0 ? null : columnPrefixBuilder.toString().toUpperCase(Locale.ENGLISH);
  }

  /**
   *   判断任一非空的列是不是有对应的值
   * */
  private boolean anyNotNullColumnHasValue(ResultMapping resultMapping, String columnPrefix, ResultSet rs) throws SQLException {
    Set<String> notNullColumns = resultMapping.getNotNullColumns();
    boolean anyNotNullColumnHasValue = true;
    if (notNullColumns != null && !notNullColumns.isEmpty()) {
      anyNotNullColumnHasValue = false;
      for (String column: notNullColumns) {
        rs.getObject(prependPrefix(column, columnPrefix));
        if (!rs.wasNull()) {
          anyNotNullColumnHasValue = true;
          break;
        }
      }
    }
    return anyNotNullColumnHasValue;
  }

  private ResultMap getNestedResultMap(ResultSet rs, String nestedResultMapId, String columnPrefix) throws SQLException {
    ResultMap nestedResultMap = configuration.getResultMap(nestedResultMapId);
    return resolveDiscriminatedResultMap(rs, nestedResultMap, columnPrefix);
  }



  /**
   *  根据结果映射、结果集和列名前缀构建 缓存Key
   *   在解析一条记录时，对于两个 ResultMap 生成的RowKey 相同，则表示这两个 ResultMap 解析完成的结果是一致的
   *
   * */
  private CacheKey createRowKey(ResultMap resultMap, ResultSetWrapper rsw, String columnPrefix) throws SQLException {
    final CacheKey cacheKey = new CacheKey();
    cacheKey.update(resultMap.getId());
    List<ResultMapping> resultMappings = getResultMappingsForRowKey(resultMap);
    //1. 如果指定的为 ResultType 或者 ResultMap 中没有指定映射
    if (resultMappings.size() == 0) {
      //1.1 如果映射的java对象为 map类型
      if (Map.class.isAssignableFrom(resultMap.getType())) {
        createRowKeyForMap(rsw, cacheKey);
      //1.2 如果没有手动指定字段和列名的映射
      } else {
        createRowKeyForUnmappedProperties(resultMap, rsw, cacheKey, columnPrefix);
      }
    //2. 如果 ResultMap 里手动指定了字段和列名的映射
    } else {
      createRowKeyForMappedProperties(resultMap, rsw, cacheKey, resultMappings, columnPrefix);
    }
    return cacheKey;
  }

  private CacheKey combineKeys(CacheKey rowKey, CacheKey parentRowKey) {
    if (rowKey.getUpdateCount() > 1 && parentRowKey.getUpdateCount() > 1) {
      CacheKey combinedKey;
      try {
        combinedKey = rowKey.clone();
      } catch (CloneNotSupportedException e) {
        throw new ExecutorException("Error cloning cache key.  Cause: " + e, e);
      }
      combinedKey.update(parentRowKey);
      return combinedKey;
    }
    return CacheKey.NULL_CACHE_KEY;
  }

  /**
   *  获取该 ResultMap 下的所有主键映射
   * */
  private List<ResultMapping> getResultMappingsForRowKey(ResultMap resultMap) {
    List<ResultMapping> resultMappings = resultMap.getIdResultMappings();
    if (resultMappings.size() == 0) {
      resultMappings = resultMap.getPropertyResultMappings();
    }
    return resultMappings;
  }

  /**
   *  如果 ResultMap 中指定了 字段到列名的映射
   * */
  private void createRowKeyForMappedProperties(ResultMap resultMap, ResultSetWrapper rsw, CacheKey cacheKey, List<ResultMapping> resultMappings, String columnPrefix) throws SQLException {
    // 遍历所有的映射
    for (ResultMapping resultMapping : resultMappings) {
       //1. 如果包含内嵌的 ResultMap 则递归调用本方法，更新缓存 Key
      if (resultMapping.getNestedResultMapId() != null && resultMapping.getResultSet() == null) {
        // Issue #392
        final ResultMap nestedResultMap = configuration.getResultMap(resultMapping.getNestedResultMapId());
        createRowKeyForMappedProperties(nestedResultMap, rsw, cacheKey, nestedResultMap.getConstructorResultMappings(),
            prependPrefix(resultMapping.getColumnPrefix(), columnPrefix));
      //2. 如果不包含内查询，则通过列名和对应的值更新缓存 key
      } else if (resultMapping.getNestedQueryId() == null) {
        final String column = prependPrefix(resultMapping.getColumn(), columnPrefix);
        final TypeHandler<?> th = resultMapping.getTypeHandler();
        List<String> mappedColumnNames = rsw.getMappedColumnNames(resultMap, columnPrefix);
        // Issue #114
        if (column != null && mappedColumnNames.contains(column.toUpperCase(Locale.ENGLISH))) {
          final Object value = th.getResult(rsw.getResultSet(), column);
          if (value != null) {
            cacheKey.update(column);
            cacheKey.update(value);
          }
        }
      }
    }
  }

  /**
   *   如果  ResultMap 中没有手动指定字段和列名的映射
   *    则通过未被映射的列名和列值更新缓存key
   * */
  private void createRowKeyForUnmappedProperties(ResultMap resultMap, ResultSetWrapper rsw, CacheKey cacheKey, String columnPrefix) throws SQLException {
    final MetaClass metaType = MetaClass.forClass(resultMap.getType());
    List<String> unmappedColumnNames = rsw.getUnmappedColumnNames(resultMap, columnPrefix);
    for (String column : unmappedColumnNames) {
      String property = column;
      if (columnPrefix != null && !columnPrefix.isEmpty()) {
        // When columnPrefix is specified, ignore columns without the prefix.
        if (column.toUpperCase(Locale.ENGLISH).startsWith(columnPrefix)) {
          property = column.substring(columnPrefix.length());
        } else {
          continue;
        }
      }
      if (metaType.findProperty(property, configuration.isMapUnderscoreToCamelCase()) != null) {
        String value = rsw.getResultSet().getString(column);
        if (value != null) {
          cacheKey.update(column);
          cacheKey.update(value);
        }
      }
    }
  }

  /**
   *  如果 ResultMap 指定的返回类型为map时
   *     通过 ResultSet 的列名和对应的列值更新缓存key
   * */
  private void createRowKeyForMap(ResultSetWrapper rsw, CacheKey cacheKey) throws SQLException {
    List<String> columnNames = rsw.getColumnNames();
    for (String columnName : columnNames) {
      final String value = rsw.getResultSet().getString(columnName);
      if (value != null) {
        cacheKey.update(columnName);
        cacheKey.update(value);
      }
    }
  }

}
