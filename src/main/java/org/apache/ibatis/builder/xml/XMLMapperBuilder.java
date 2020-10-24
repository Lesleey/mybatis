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

    //之前由于一些顺序的原因导致一些cacheRef,resultMap, statment中的resultMap引用抛出未完成异常，在这里继续进行解析。
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
      //1.配置namespace
      String namespace = context.getStringAttribute("namespace");
      if (namespace.equals("")) {
        throw new BuilderException("Mapper's namespace cannot be empty");
      }
      builderAssistant.setCurrentNamespace(namespace);
      //2.解析cache-ref节点
      cacheRefElement(context.evalNode("cache-ref"));
      //3.配置cache
      cacheElement(context.evalNode("cache"));
      //4.配置parameterMap(已经废弃,老式风格的参数映射)，不看了，不常用
      parameterMapElement(context.evalNodes("/mapper/parameterMap"));
      //5.配置resultMap(高级功能)
      resultMapElements(context.evalNodes("/mapper/resultMap"));
      //6.配置sql(定义可重用的 SQL 代码段)
      sqlElement(context.evalNodes("/mapper/sql"));
      //7.配置select|insert|update|delete TODO
      buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing Mapper XML. Cause: " + e, e);
    }
  }

  //7.配置select|insert|update|delete
  private void buildStatementFromContext(List<XNode> list) {
    //调用7.1构建语句
    if (configuration.getDatabaseId() != null) {
      buildStatementFromContext(list, configuration.getDatabaseId());
    }
    buildStatementFromContext(list, null);
  }

  //7.1构建语句
  private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
    for (XNode context : list) {
      //构建所有语句,一个mapper下可以有很多select
      //语句比较复杂，核心都在这里面，所以调用XMLStatementBuilder来创建MappedStatment
      final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
      try {
          //核心XMLStatementBuilder.parseStatementNode
        statementParser.parseStatementNode();
      } catch (IncompleteElementException e) {
          //如果出现SQL语句不完整，把它记下来，塞到configuration去
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

  //2.配置cache-ref,在这样的 情况下你可以使用 cache-ref 元素来引用另外一个mapper缓存。
//<cache-ref namespace="com.someone.application.data.SomeMapper"/>
  private void cacheRefElement(XNode context) {
    if (context != null) {
      //给当前configuration对象cacheRefMap中，添加当前namespace,和要引用的namespace
      configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));
      //通过CacheRefResolver解析器获得当前引用的namespace所使用的缓存。
      CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant, context.getStringAttribute("namespace"));
      try {
        //主要是通过它，判断当前引用的namespace是否已经解析过，或者有没有对应的缓存，如果没有，则会抛出异常，如果有，则把他赋值到builderAssistant的currentCache变量上。
        cacheRefResolver.resolveCacheRef();
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

  //4.配置parameterMap
  //已经被废弃了!老式风格的参数映射。可以忽略
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

  //5.配置resultMap,高级功能
  private void resultMapElements(List<XNode> list) throws Exception {
      //基本上就是循环把resultMap加入到Configuration里去,保持2份，一份缩略，一分全名
    for (XNode resultMapNode : list) {
      try {
          //循环调resultMapElement
        resultMapElement(resultMapNode);
      } catch (IncompleteElementException e) {
      }
    }
  }

  //5.1 配置resultMap
  private ResultMap resultMapElement(XNode resultMapNode) throws Exception {
    return resultMapElement(resultMapNode, Collections.<ResultMapping> emptyList());
  }

  //5.1 配置resultMap
  private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings) throws Exception {
    //错误上下文
/*  <resultMap type="role" id="roleMap" extends="baseResultMap">
        <id column="id" property="id" javaType="long" jdbcType="BIGINT" />
        <result column="role_name" property="roleName" javaType="string" jdbcType="VARCHAR" />

        <association property="permission" javaType="demo.entity.Permission"  resultMap="permission">

        </association>
    </resultMap>*/
    ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());
    //如果没有Id说明当前此方法的resultMap节点是一个association/collection/case，则封装一个id作为该ResultMap的唯一标识。
    String id = resultMapNode.getStringAttribute("id",
        resultMapNode.getValueBasedIdentifier());
    //获取该resultMap所表示的类,从当前resultMapNode节点取值，type|ofTpe|resultType|javaType
    String type = resultMapNode.getStringAttribute("type",
        resultMapNode.getStringAttribute("ofType",
            resultMapNode.getStringAttribute("resultType",
                resultMapNode.getStringAttribute("javaType"))));
    //取当前节点的"extends"属性的值，extends如何使用看官网。
    String extend = resultMapNode.getStringAttribute("extends");
    Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
    Class<?> typeClass = resolveClass(type);
    Discriminator discriminator = null;
    List<ResultMapping> resultMappings = new ArrayList<ResultMapping>();
    resultMappings.addAll(additionalResultMappings);
    //获取该resultMap | assocation | collection等下的所有子节点
    List<XNode> resultChildren = resultMapNode.getChildren();
    //遍历所有子节点
    for (XNode resultChild : resultChildren) {
      if ("constructor".equals(resultChild.getName())) {
        //解析result map的constructor
        processConstructorElement(resultChild, typeClass, resultMappings);
      } else if ("discriminator".equals(resultChild.getName())) {
        //解析result map的discriminator
        discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
      } else {
        //如果该子节点是id节点，则给flags集合加上一个id标记。
        List<ResultFlag> flags = new ArrayList<ResultFlag>();
        if ("id".equals(resultChild.getName())) {
          flags.add(ResultFlag.ID);
        }
        //buildResultMappingFromContext()方法,通过这个子节点构建ResultMapping
        resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
      }
    }
    //最后再调ResultMapResolver得到ResultMap
    ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
    try {
      return resultMapResolver.resolve();
    } catch (IncompleteElementException  e) {
      configuration.addIncompleteResultMap(resultMapResolver);
      throw e;
    }
  }

//解析result map的constructor
//<constructor>
//  <idArg column="blog_id" javaType="int"/>
//</constructor>
  private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    List<XNode> argChildren = resultChild.getChildren();
    for (XNode argChild : argChildren) {
      List<ResultFlag> flags = new ArrayList<ResultFlag>();
      //结果标志加上ID和CONSTRUCTOR
      flags.add(ResultFlag.CONSTRUCTOR);
      if ("idArg".equals(argChild.getName())) {
        flags.add(ResultFlag.ID);
      }
      resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
    }
  }

//解析result map的discriminator
//<discriminator javaType="int" column="draft">
//  <case value="1" resultType="DraftPost"/>
//</discriminator>
  private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String typeHandler = context.getStringAttribute("typeHandler");
    Class<?> javaTypeClass = resolveClass(javaType);
    @SuppressWarnings("unchecked")
    Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    Map<String, String> discriminatorMap = new HashMap<String, String>();
    for (XNode caseChild : context.getChildren()) {
      String value = caseChild.getStringAttribute("value");
      String resultMap = caseChild.getStringAttribute("resultMap", processNestedResultMappings(caseChild, resultMappings));
      discriminatorMap.put(value, resultMap);
    }
    return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
  }

  //6 配置sql(定义可重用的 SQL 代码段)
  private void sqlElement(List<XNode> list) throws Exception {
    if (configuration.getDatabaseId() != null) {
      sqlElement(list, configuration.getDatabaseId());
    }
    sqlElement(list, null);
  }

  private void sqlElement(List<XNode> list, String requiredDatabaseId) throws Exception {
    for (XNode context : list) {
      //获取当前sql节点的dataBaseId属性的值
      String databaseId = context.getStringAttribute("databaseId");
      //获取当前sql节点的id的值
      String id = context.getStringAttribute("id");
      //给当前sql片段的id添加currentNamespace前缀。
      id = builderAssistant.applyCurrentNamespace(id, false);
      if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
        //将sql片段的节点放入configuration对象的sqlFragments中
        sqlFragments.put(id, context);
      }
    }
  }

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
        //当前sql片段的databaseId为null，只有当前sql片段的id在configuraion对象的sqlFragments中存在，且对应的context的dataBaseId为null时会被覆盖。
        if (context.getStringAttribute("databaseId") != null) {
          return false;
        }
      }
    }
    return true;
  }

  //5.1.1 构建resultMapping
  private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) throws Exception {
	//<id property="id" column="author_id"/>
	//<result property="username" column="author_username"/>
    //只有当前子节点名为id时，flags才会有值R为esultFlag.ID
    //下方就是获取resultMap子节点的各个属性
    String property = context.getStringAttribute("property");
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String nestedSelect = context.getStringAttribute("select");
    //处理嵌套的result map，如果节点名为association/collection/case时，则会先调用 resultMapElement(context, resultMappings);把当前节点构建成ResultMap，然后注册到configuraion中，然后把id返回。
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
    //又去调builderAssistant.buildResultMapping，拿到这些属性构建ResultMapping
    return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect, nestedResultMap, notNullColumn, columnPrefix, typeHandlerClass, flags, resulSet, foreignColumn, lazy);
  }
  
  //5.1.1.1 处理嵌套的result map
  private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings) throws Exception {
	  //处理association|collection|case
    if ("association".equals(context.getName())
        || "collection".equals(context.getName())
        || "case".equals(context.getName())) {
    	
//    	<resultMap id="blogResult" type="Blog">
//    	  <association property="author" column="author_id" javaType="Author" select="selectAuthor"/>
//    	</resultMap>
//如果不是嵌套查询
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
