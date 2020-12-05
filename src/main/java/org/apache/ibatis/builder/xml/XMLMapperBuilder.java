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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.*;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.ResultMapResolver;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * @author Clinton Begin
 */
/**
 * XML映射构建器，D继承BaseBuilder
 *
 */
public class XMLMapperBuilder extends BaseBuilder {

  /**
   *  mapper文件对应的解析器
   * */
  private XPathParser parser;

  /**
   *  映射器构建助手
   * */
  private MapperBuilderAssistant builderAssistant;

  /**
   * 存放sql片段的哈希表: id 《=》 sql节点
   * */
  private Map<String, XNode> sqlFragments;

  /**
   * dao接口对应的xml文件的资源路径
   * */
  private String resource;

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(reader, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(reader, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(inputStream, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    super(configuration);
    this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
    this.parser = parser;
    this.sqlFragments = sqlFragments;
    this.resource = resource;
  }

  /**
   *  解析 mapper 文件
   * */
  public void parse() {
    //1. 如果没有加载过才会加载，避免重复加载
    if (!configuration.isResourceLoaded(resource)) {
      //2. 解析 mapper 节点
      configurationElement(parser.evalNode("/mapper"));
      //3. 记录当前加载完成的资源路径
      configuration.addLoadedResource(resource);
      //4. 记录加载完成的dao对象，并绑定映射代理工厂
      bindMapperForNamespace();
    }

    //5. 由于mapper文件解析顺序的原因， 可能会导致导致resultMap、cacheRef、 statement中的一些引用所在的mapper文件还未解析，这种情况会抛出InCompleted异常，此时会将这些抛出该异常的解析器
    //记录下来， 在每个mapper文件解析完成都会重新尝试调用这些解析器的解析方法
    parsePendingResultMaps();
    parsePendingChacheRefs();
    parsePendingStatements();
  }

  public XNode getSqlFragment(String refid) {
    return sqlFragments.get(refid);
  }


  /**
   *  解析mapper文件中的<mapper>节点
   * */
  private void configurationElement(XNode context) {
    try {
      //1.配置namespace(一般为对应 dao 接口的全限定符)
      String namespace = context.getStringAttribute("namespace");
      if (namespace.equals("")) {
        throw new BuilderException("Mapper's namespace cannot be empty");
      }
      builderAssistant.setCurrentNamespace(namespace);
      //2.解析cache-ref节点
      cacheRefElement(context.evalNode("cache-ref"));
      //3.解析cache节点
      cacheElement(context.evalNode("cache"));
      //4.配置parameterMap(已经废弃,老式风格的参数映射)，已废弃
      parameterMapElement(context.evalNodes("/mapper/parameterMap"));
      //5.配置resultMap(高级功能)
      resultMapElements(context.evalNodes("/mapper/resultMap"));
      //6.解析sql节点(定义可重用的 SQL 代码段)
      sqlElement(context.evalNodes("/mapper/sql"));
      //7.配置select|insert|update|delete
      buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing Mapper XML. Cause: " + e, e);
    }
  }

  /**
   *  解析 select | insert | update | delete 节点
   *   解析符合当前数据库环境的（指定的databaseId和当前数据库环境一致）节点和未指定databaseId 的默认所有数据库都可以使用的节点
   *   如果指定了dabaseId 且符合当前数据库环境的节点的id、和未指定databaseId 的节点的 id 相同，则优先使用前者
   * */
  private void buildStatementFromContext(List<XNode> list) {

    if (configuration.getDatabaseId() != null) {
      buildStatementFromContext(list, configuration.getDatabaseId());
    }
    buildStatementFromContext(list, null);
  }

  /**
   * @param list 所有的sql语句节点
   * @param requiredDatabaseId  需要匹配的databaseId
   * */
  private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
    //1. 遍历所有的 sql 语句节点
    for (XNode context : list) {
      //1.1 构建 XmlstatementParser 解析类进行解析
      final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
      try {
        statementParser.parseStatementNode();
      } catch (IncompleteElementException e) {
        //1.2 如果解析过程中，抛出 IncompleteElementException, 说明需要的某些属性可能还未解析，则将当前 StatmentParser 保存起来。
        configuration.addIncompleteStatement(statementParser);
      }
    }
  }

  private void parsePendingResultMaps() {
    Collection<ResultMapResolver> incompleteResultMaps = configuration.getIncompleteResultMaps();
    synchronized (incompleteResultMaps) {
      Iterator<ResultMapResolver> iter = incompleteResultMaps.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolve();
          iter.remove();
        } catch (IncompleteElementException e) {
          // ResultMap is still missing a resource...
        }
      }
    }
  }

  private void parsePendingChacheRefs() {
    Collection<CacheRefResolver> incompleteCacheRefs = configuration.getIncompleteCacheRefs();
    synchronized (incompleteCacheRefs) {
      Iterator<CacheRefResolver> iter = incompleteCacheRefs.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolveCacheRef();
          iter.remove();
        } catch (IncompleteElementException e) {
          // Cache ref is still missing a resource...
        }
      }
    }
  }

  private void parsePendingStatements() {
    Collection<XMLStatementBuilder> incompleteStatements = configuration.getIncompleteStatements();
    synchronized (incompleteStatements) {
      Iterator<XMLStatementBuilder> iter = incompleteStatements.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().parseStatementNode();
          iter.remove();
        } catch (IncompleteElementException e) {
          // Statement is still missing a resource...
        }
      }
    }
  }

  /**
   *  配置cache-ref,通过该配置可以使得当前 mapper 和指定的 mapper 共享相同的缓存配置和实例
   * */
  private void cacheRefElement(XNode context) {
    if (context != null) {
      //1. 在全局配置类中记录引用关系
      configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));
      //2. 通过CacheRefResolver解析器构建当前 mapper 使用的缓存
      CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant, context.getStringAttribute("namespace"));
      try {
        //3. 解析的主要流程，获取并使用指定命名空间的缓存对象
        cacheRefResolver.resolveCacheRef();
      //4. 如果指定的mapper文件还没有解析，则会抛出 IncompleteElementException 异常，此时会记录当前的缓存引用解析器，在mapper全部解析完成之后，会重新使用该解析器进行解析
      } catch (IncompleteElementException e) {
        configuration.addIncompleteCacheRef(cacheRefResolver);
      }
    }
  }

  /**
   *  https://mybatis.org/mybatis-3/zh/sqlmap-xml.html#cache
   *  解析 <cache/> 节点
   * */
  private void cacheElement(XNode context) throws Exception {
    if (context != null) {
      //1. 获取缓存节点的相关属性：包括所使用的缓存对象别名、换出策略、刷新间隔、缓存对象的数量
      String type = context.getStringAttribute("type", "PERPETUAL");
      Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);
      String eviction = context.getStringAttribute("eviction", "LRU");
      Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);
      Long flushInterval = context.getLongAttribute("flushInterval");
      Integer size = context.getIntAttribute("size");
      boolean readWrite = !context.getBooleanAttribute("readOnly", false);
      boolean blocking = context.getBooleanAttribute("blocking", false);
      //2. 获取该节点内部的自定义属性
      Properties props = context.getChildrenAsProperties();
      //3. 为该mapper构建缓存对象
      builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
    }
  }


  private void parameterMapElement(List<XNode> list) throws Exception {
    for (XNode parameterMapNode : list) {
      String id = parameterMapNode.getStringAttribute("id");
      String type = parameterMapNode.getStringAttribute("type");
      Class<?> parameterClass = resolveClass(type);
      List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
      List<ParameterMapping> parameterMappings = new ArrayList<ParameterMapping>();
      for (XNode parameterNode : parameterNodes) {
        String property = parameterNode.getStringAttribute("property");
        String javaType = parameterNode.getStringAttribute("javaType");
        String jdbcType = parameterNode.getStringAttribute("jdbcType");
        String resultMap = parameterNode.getStringAttribute("resultMap");
        String mode = parameterNode.getStringAttribute("mode");
        String typeHandler = parameterNode.getStringAttribute("typeHandler");
        Integer numericScale = parameterNode.getIntAttribute("numericScale");
        ParameterMode modeEnum = resolveParameterMode(mode);
        Class<?> javaTypeClass = resolveClass(javaType);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        @SuppressWarnings("unchecked")
        Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
        ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property, javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
        parameterMappings.add(parameterMapping);
      }
      builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
    }
  }

  /**
   *  解析所有的 resultMap 标签
   * */
  private void resultMapElements(List<XNode> list) throws Exception {
    for (XNode resultMapNode : list) {
      try {
        resultMapElement(resultMapNode);
      } catch (IncompleteElementException e) {
      }
    }
  }

  /**
   *  解析 resultMap标签
   * */
  private ResultMap resultMapElement(XNode resultMapNode) throws Exception {
    return resultMapElement(resultMapNode, Collections.<ResultMapping> emptyList());
  }

  /**
   *  解析 <resultMap/>、<association/>、<collection/>、<case/> 节点
   * @param resultMapNode  当前的resultMap节点， 可以为 <resultMap/>、<association/>、<collection/>、<case/>
   * @param additionalResultMappings  附加的 resultMapping（当 resultMapNode 节点不是<resultMap/> 节点时，当前resultMapNode外部，所在的<resultMap内部）
   *
   * */
  private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings) throws Exception {

    ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());
    //1. 获取当前resultMap节点的唯一标示, 如果为association/collection/case节点， 则构建相对于根节点的路径作为唯一标示
    String id = resultMapNode.getStringAttribute("id",
        resultMapNode.getValueBasedIdentifier());
    //2 获取该resultMap所表示的java类型,按照 type|ofTpe|resultType|javaType 顺序获取对应属性的值(别名或者全限定符)
    String type = resultMapNode.getStringAttribute("type",
        resultMapNode.getStringAttribute("ofType",
            resultMapNode.getStringAttribute("resultType",
                resultMapNode.getStringAttribute("javaType"))));
    //3. 取当前节点的"extends"属性的值，extends如何使用看官网。
    String extend = resultMapNode.getStringAttribute("extends");
    //4. 获取当前节点是否配置自动映射
    Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
    Class<?> typeClass = resolveClass(type);
    Discriminator discriminator = null;
    //5. 解析当前节点下的所有子节点，并将每个子节点都构建成 ResultMapping 实例，并添加到该集合中
    List<ResultMapping> resultMappings = new ArrayList<ResultMapping>();
    resultMappings.addAll(additionalResultMappings);
    List<XNode> resultChildren = resultMapNode.getChildren();
    for (XNode resultChild : resultChildren) {
      //5.1 处理 <constructor/>节点
      if ("constructor".equals(resultChild.getName())) {
        processConstructorElement(resultChild, typeClass, resultMappings);
      //5.2 处理 <discriminator />节点
      } else if ("discriminator".equals(resultChild.getName())) {
        discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
      //5.3  处理简单的节点 <id/> 和 <result/>
      } else {
        List<ResultFlag> flags = new ArrayList<ResultFlag>();
        if ("id".equals(resultChild.getName())) {
          flags.add(ResultFlag.ID);
        }
        resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
      }
    }
    ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
    try {
      return resultMapResolver.resolve();
    } catch (IncompleteElementException  e) {
      configuration.addIncompleteResultMap(resultMapResolver);
      throw e;
    }
  }

  /**
   * @param resultChild  正在构建的ResultMap节点的子节点 <constructor/>
   * @param resultType 正在构建的ResultMap的java类型
   * @param resultMappings  附加的 resultMapping（正在构建的ResultMap节点内部，当前resultChild节点外部解析完成的 resultMapping）
   * */
  private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    List<XNode> argChildren = resultChild.getChildren();
    //1. 遍历所有的子节点
    for (XNode argChild : argChildren) {
      List<ResultFlag> flags = new ArrayList<ResultFlag>();
      //1.1 添加标志，标记当前 ResultMapping 用于构造对象
      flags.add(ResultFlag.CONSTRUCTOR);
      if ("idArg".equals(argChild.getName())) {
        flags.add(ResultFlag.ID);
      }
      //1.2 将构建出来的对象添加到 ResultMapping 集合中
      resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
    }
  }

  /**
   *  构建Discriminator 鉴别器： 根据结果值决定使用那个ResultMap,类似于java里的 Switch语句
   * */
  private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    //1. 获取比较值的列
    String column = context.getStringAttribute("column");
    //2. 获取该列的javaType、jdbcType和对应的类型处理器。
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String typeHandler = context.getStringAttribute("typeHandler");
    Class<?> javaTypeClass = resolveClass(javaType);
    @SuppressWarnings("unchecked")
    Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    //3. key: 需要匹配的值, value： 对应的 ResultMap的全局配置类的唯一标识符
    Map<String, String> discriminatorMap = new HashMap<String, String>();
    //4. 遍历该 鉴别器下的所有子节点
    for (XNode caseChild : context.getChildren()) {
      //4.1 获取需要匹配的值
      String value = caseChild.getStringAttribute("value");
      //4.2 处理内嵌的 resultMap(case 节点)
      String resultMap = caseChild.getStringAttribute("resultMap", processNestedResultMappings(caseChild, resultMappings));
      discriminatorMap.put(value, resultMap);
    }
    //5. 通过建造者模式构建鉴别器
    return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
  }

  /**
   *  配置 sql 节点，定义可重用的 sql 代码片段
   *
   * */
  private void sqlElement(List<XNode> list) throws Exception {

    if (configuration.getDatabaseId() != null) {
      sqlElement(list, configuration.getDatabaseId());
    }
    sqlElement(list, null);
  }

  /**
   * @param list 当前mapper文件中，所有的sql节点
   * @param requiredDatabaseId 需要匹配的 databaseid
   *       将所有 sql 节点匹配当前数据库环境的 sql 片段保存起来
   *       1. 如果sql 节点中没有指定 databaseId 则表示任何数据库环境都可以使用
   *       2. 如果 sql 节点中指定了 databaseId， 且和当前数据库环境匹配，则表示可以使用，此时如果 sql 节点的 id 存在与 （1） 中相同，则优先级高于 （1）
   * */
  private void sqlElement(List<XNode> list, String requiredDatabaseId) throws Exception {
    // 遍历所有的 sql 节点
    for (XNode context : list) {
      //1. 获取当前sql节点的dataBaseId属性的值
      String databaseId = context.getStringAttribute("databaseId");
      //2. 获取当前sql节点的id的值
      String id = context.getStringAttribute("id");
      //3. 给当前sql片段的id添加currentNamespace前缀。
      id = builderAssistant.applyCurrentNamespace(id, false);
      //4. 如果当前 sql 片段匹配当前环境，则将其添加到 sqlFragments 属性中
      if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
        sqlFragments.put(id, context);
      }
    }
  }

  /**
   * @param id 当前 sql 指定的唯一标识符节点
   * @param databaseId 当前 sql 节点指定的 databaseId
   * @param requiredDatabaseId 全局配置类中指定的 databaseId
   *     判断当前的 sql 片段是否匹配当前数据库环境
   * */
  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {

    if (requiredDatabaseId != null) {
      if (!requiredDatabaseId.equals(databaseId)) {
        return false;
      }
    } else {
      if (databaseId != null) {
        return false;
      }
      if (this.sqlFragments.containsKey(id)) {
        XNode context = this.sqlFragments.get(id);
        if (context.getStringAttribute("databaseId") != null) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   *   根据简单节点构建 ResultMapping
   * @param context  <constructor/>节点的<idArg/> 和 <arg/> 子节点、<resultMap/>节点内部的 <id/> 节点和 <result/>节点
   * @param resultType 当前节点所在 </resultMap/> 节点的对应的 java类型
   * @param flags 结果标记，标记当前是否为 <id/> 或者 <idArg/>
   * */
  private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) throws Exception {
	//1. 获取该节点内部的属性
    String property = context.getStringAttribute("property");
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String nestedSelect = context.getStringAttribute("select");
    //2. 处理内嵌的 <resultMap/> 节点
    String nestedResultMap = context.getStringAttribute("resultMap",
        processNestedResultMappings(context, Collections.<ResultMapping> emptyList()));
    String notNullColumn = context.getStringAttribute("notNullColumn");
    String columnPrefix = context.getStringAttribute("columnPrefix");
    String typeHandler = context.getStringAttribute("typeHandler");
    String resulSet = context.getStringAttribute("resultSet");
    String foreignColumn = context.getStringAttribute("foreignColumn");
    boolean lazy = "lazy".equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled() ? "lazy" : "eager"));
    Class<?> javaTypeClass = resolveClass(javaType);
    @SuppressWarnings("unchecked")
    Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    //3. 通过构建助手构建 ResultMapping 对象
    return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect, nestedResultMap, notNullColumn, columnPrefix, typeHandlerClass, flags, resulSet, foreignColumn, lazy);
  }
  
  //5.1.1.1 处理嵌套的result map
  private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings) throws Exception {
	  //处理association|collection|case
    if ("association".equals(context.getName())
        || "collection".equals(context.getName())
        || "case".equals(context.getName())) {
    	
      if (context.getStringAttribute("select") == null) {
    	//则递归调用5.1 resultMapElement
        ResultMap resultMap = resultMapElement(context, resultMappings);
        return resultMap.getId();
      }
    }
    return null;
  }

  /**
   *  为dao对象绑定映射代理工厂，并将映射关系记录到映射注册器中
   * */
  private void bindMapperForNamespace() {
    //1. 获取当前 mapper 文件的命名空间
    String namespace = builderAssistant.getCurrentNamespace();
    if (namespace != null) {
      //2. 获取命名空间（dao的全限定符号）对应的类对象
      Class<?> boundType = null;
      try {
        boundType = Resources.classForName(namespace);
      } catch (ClassNotFoundException e) {
        //ignore, bound type is not required
      }
      //3. 记录解析完成的dao对象，并为对象该绑定映射代理工厂
      if (boundType != null) {
        if (!configuration.hasMapper(boundType)) {
          // Spring may not know the real resource name so we set a flag
          // to prevent loading again this resource from the mapper interface
          // look at MapperAnnotationBuilder#loadXmlResource

          configuration.addLoadedResource("namespace:" + namespace);
          configuration.addMapper(boundType);
        }
      }
    }
  }

}
