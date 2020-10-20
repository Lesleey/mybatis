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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;


/**
 * XML配置构建器，建造者模式,继承BaseBuilder
 *   通过解析 ibatis 配置文件, 通过获取各个节点(configuration、 typeAliase、plugins等等)的配置，构建Configuration 类对应的属性。
 *   该类的作用就是解析配置文件，获取 mybatis的配置类 Configuration
 */
public class XMLConfigBuilder extends BaseBuilder {

  /**
   *  解析标识，避免重复解析
   * */
  private boolean parsed;

  /**
   * Xml 解析器， 用来获取 xml 文件指定节点的信息
   * */
  private XPathParser parser;

  /**
   *  当岗前所处的环境
   * */
  private String environment;


  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public  XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  /**
   * @param inputStream 配置文件对应的流
   * @param environment 通过参数指定的所使用的环境
   * @param props  通过参数指定的键值对，用来解析配置文件中的 ${} 占位符
   *
   * */
  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  /**
   * @param parser xml解析器，用来获取 xml 文件中节点的属性和值
   * @param environment 通过方法参数指定的环境
   * @param props  通过方法参数指定的属性（键值对）
   * */
  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    //1. 初始化全局的配置类 Configuration
    super(new Configuration());

    //2. 设置异常上下文，表示 sqlMapper 当前正在处理 Mapper 中
    ErrorContext.instance().resource("SQL Mapper Configuration");

    //3. 初始化对象
    this.configuration.setVariables(props);
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  /**
   *  解析配置文件
   * */
  public Configuration parse() {
    //1. 解析标识，避免重复解析
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    parsed = true;

    //2. 解析配置文件document对象的 <configuration> 节点，构建 Configuration对象
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  /**
   *  解析流程
   * */
  private void parseConfiguration(XNode root) {
    try {
      //1.解析properties节点
      propertiesElement(root.evalNode("properties"));
      //2.解析类型别名节点
      typeAliasesElement(root.evalNode("typeAliases"));
      //3.解析插件节点 拦截器模式
      pluginElement(root.evalNode("plugins"));
      //4.解析对象工厂节点
      objectFactoryElement(root.evalNode("objectFactory"));
      //5.解析对象包装工厂节点
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      //6.解析设置节点
      settingsElement(root.evalNode("settings"));
      //7.解析环境节点
      environmentsElement(root.evalNode("environments"));
      //8.解析databaseIdProvider节点
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      //9.解析类型处理器节点
      typeHandlerElement(root.evalNode("typeHandlers"));
      //10.解析映射器节点
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }


  /**
   *  解析类型别名节点，注册类型别名包括两种方式
   *   1. 包名, 2. 类名
   * */
  private void typeAliasesElement(XNode parent) {

    if (parent != null) {
      //1. 遍历该节点的所有子节点
      for (XNode child : parent.getChildren()) {

        //1.1 通过包名进行注册
        if ("package".equals(child.getName())) {
          String typeAliasPackage = child.getStringAttribute("name");
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);

        //1.2 通过类名进行注册
        } else {

          String alias = child.getStringAttribute("alias");
          String type = child.getStringAttribute("type");
          try {
            Class<?> clazz = Resources.classForName(type);
            if (alias == null) {
              typeAliasRegistry.registerAlias(clazz);
            } else {
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }


  /**
   *   注册插件，插件的作用就是包装 mybatis 的四大对象， 比如 分页插件pageHelper
   *
   * */
  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      //1. 遍历所有的子节点
      for (XNode child : parent.getChildren()) {
        //1.1 获取插件的类型（别名），并实例化
        String interceptor = child.getStringAttribute("interceptor");

        //1.2 获取<plugin>的所有子节点作为属性键值对，并设置到插件中
        Properties properties = child.getChildrenAsProperties();
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).newInstance();
        interceptorInstance.setProperties(properties);

        //1.3 将实例化的插件对象添加到包装链中
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  /**
   *  对象工厂，mybatis通过对象工厂完成实例的初始化，在通过ORM映射工作之前，会通过该工厂初始化一个对应类型的实例，然后才会赋值。
   * */
  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      //1. 获取对象工厂的类型（别名），并实例化
      String type = context.getStringAttribute("type");

      //2. 获取<objectfacotry>节点内部的所有子节点作为键值对，并添加到对象工厂实例中
      Properties properties = context.getChildrenAsProperties();
      ObjectFactory factory = (ObjectFactory) resolveClass(type).newInstance();
      factory.setProperties(properties);

      //3. 设置到全局配置类中，作为初始化对象的工厂
      configuration.setObjectFactory(factory);
    }
  }

  /**
   *  对象包装工厂，用来包装返回的 result 对象，可以在这里对返回的对象进行自定义的操作，比如加密某个字段、脱敏某个字段
   * */
  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      //1. 获取该对象包装工厂的类型（别名）， 然后进行初始化
      String type = context.getStringAttribute("type");
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).newInstance();

      //2. 添加到全局配置类中
      configuration.setObjectWrapperFactory(factory);
    }
  }

  /**
   *     获取属性键值对 参见 org.apache.ibatis.builder.properties.CustomPropertiesTest
   *       优先级: 参数指定 > 通过url|resource获取 >  内部节点指定
   * */
  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {

      //1 获取该节点子节点的 name 和 value 属性
      Properties defaults = context.getChildrenAsProperties();

      //2.获取该节点的resource属性和url属性（* 注意：resource和url不能同时存在）
      String resource = context.getStringAttribute("resource");
      String url = context.getStringAttribute("url");
      if (resource != null && url != null) {
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }

      //3. 获取 resource 或者 url 对应的所有键值对，并添加到 defaults 中
      if (resource != null) {
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        defaults.putAll(Resources.getUrlAsProperties(url));
      }

      //4. 将 configuration 对象原有的属性键值对添加到 defaults 中
      Properties vars = configuration.getVariables();
      if (vars != null) {
        defaults.putAll(vars);
      }

      //4. 将新的属性键值对添加到 解析器和配置对象中
      parser.setVariables(defaults);
      configuration.setVariables(defaults);
    }
  }

  /**
   *    https://mybatis.org/mybatis-3/zh/configuration.html#settings
   *    修改 mybatis 运行行为，包括缓存、延迟加载、映射行为等等
   * */
  private void settingsElement(XNode context) throws Exception {
    if (context != null) {
      Properties props = context.getChildrenAsProperties();

      //检查下是否在Configuration类里都有相应的setter方法（没有拼写错误）
      MetaClass metaConfig = MetaClass.forClass(Configuration.class);
      for (Object key : props.keySet()) {
        if (!metaConfig.hasSetter(String.valueOf(key))) {
          throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
        }
      }
      configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
      configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
      configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
      configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
      configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), true));
      configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
      configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
      configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
      configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
      configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));  //超时时间
      configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
      configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
      configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
      configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
      configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
      configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
      configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
      configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
      configuration.setLogPrefix(props.getProperty("logPrefix"));
      configuration.setLogImpl(resolveClass(props.getProperty("logImpl")));
      configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
    }
  }
  
  /**
   *  配置 mybatis 运行所需环境：数据库、事务类型等等
   * */
  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      //1. 获取 <enviroments> 节点指定的使用环境
      if (environment == null) {
        environment = context.getStringAttribute("default");
      }

      for (XNode child : context.getChildren()) {
        String id = child.getStringAttribute("id");
        //2. 遍历所有内部<enviroment>， 只有指定的环境配置才会被解析
        if (isSpecifiedEnvironment(id)) {
          //3. 解析配置，获取事务工厂、数据源工厂和数据源
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          DataSource dataSource = dsFactory.getDataSource();

          //4. 构建 Environment 对象，设置到全局配置类中
          Environment.Builder environmentBuilder = new Environment.Builder(id)
              .transactionFactory(txFactory)
              .dataSource(dataSource);
          configuration.setEnvironment(environmentBuilder.build());
        }
      }
    }
  }

	//8.databaseIdProvider
	//可以根据不同数据库执行不同的SQL，在Mybatis配置文件配置dataBaseIdProvider，然后配置属性，如果没有配置属性，则默认使用的是数据库产品名。
    //然后在insert |delete | update | select 中指定dataBaiseId。 oracle
//	<databaseIdProvider type="VENDOR">
//	  <property name="SQL Server" value="sqlserver"/>
//	  <property name="DB2" value="db2"/>        
//	  <property name="Oracle" value="oracle" />
//	</databaseIdProvider>
  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility
      //与老版本兼容
      if ("VENDOR".equals(type)) {
          type = "DB_VENDOR";
      }
      Properties properties = context.getChildrenAsProperties();
      //"DB_VENDOR"-->VendorDatabaseIdProvider
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).newInstance();
      databaseIdProvider.setProperties(properties);
    }
    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
      //通过当前默认环境的dataSource,获得数据库产品名，然后根据产品名，获取“别名”，也就是你在<property>属性中，配置的值，
      //eg: <property name="SQL Server" value="sqlserver"/> "SQL Server"产品名， "sqlserver":别名
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      //然后设置当前configuration的databaseId为你取的“别名”
      configuration.setDatabaseId(databaseId);
    }
  }

  //7.1事务管理器
//<transactionManager type="JDBC">
//  <property name="..." value="..."/>
//</transactionManager>
  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      //首先获取该transactionManager节点type属性的值，然后通过别名注册器获取对应的类对象，然后返回该对象的实例
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
		//根据type="JDBC"解析返回适当的TransactionFactory
      TransactionFactory factory = (TransactionFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

	//7.2数据源
//<dataSource type="POOLED">
//  <property name="driver" value="${driver}"/>
//  <property name="url" value="${url}"/>
//  <property name="username" value="${username}"/>
//  <property name="password" value="${password}"/>
//</dataSource>
  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      //首先获取该dataSource节点type属性的值，然后通过别名注册器获取对应的类对象，然后返回该对象的实例
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
		//根据type="POOLED",PooledDataSourceFactory
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).newInstance();
      //通过这，给该数据源工厂内部的dataSource设置属性，password,url,jdbc,driver,也可以给driver设置一些额外的属性
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

	//9.类型处理器
    // 类型处理器，主要是在预编译，和返回结果时，把java类型的值转化为jdbc类型的值，或者从jdbc类型的值转化为java类型的值使用的
    //主要有两种配置方式，包名注册，或者直接通过类进行注册，都是通过使用重载的方法，向configuration对象的typeHandlerRegistry中注册类型处理器。
  //两种，方式，一个直接指定handler类，和处理的javaType和jdbcType, 一种使用注解方式，MappedTypes和MappedJdbcTypes注解，各种组合
//	<typeHandlers>
//	  <typeHandler handler="org.ibatis.example.ExampleTypeHandler"/>
//	</typeHandlers>
  private void typeHandlerElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        //如果是package
        if ("package".equals(child.getName())) {
          String typeHandlerPackage = child.getStringAttribute("name");
          //（一）调用TypeHandlerRegistry.register，去包下找所有类
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          //如果是typeHandler
          String javaTypeName = child.getStringAttribute("javaType");
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          String handlerTypeName = child.getStringAttribute("handler");
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          //（二）调用TypeHandlerRegistry.register(以下是3种不同的参数形式)
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

	//10.映射器
    //mapper文件的扫描，包括四种方式，package, resource,url, class,其中后三种一个mapper节点只能存在一种方式
  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          //10.4自动扫描包下所有映射器
          String mapperPackage = child.getStringAttribute("name");
          configuration.addMappers(mapperPackage);
        } else {
          String resource = child.getStringAttribute("resource");
          String url = child.getStringAttribute("url");
          String mapperClass = child.getStringAttribute("class");
          if (resource != null && url == null && mapperClass == null) {
            //10.1使用resource
            ErrorContext.instance().resource(resource);
            InputStream inputStream = Resources.getResourceAsStream(resource);
            //通过该流创建一个XMLMapperBuilder，对象，通过建造者模式解析节点，然后把配置添加到configuration对象中
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url != null && mapperClass == null) {
            //10.2使用绝对url路径
            ErrorContext.instance().resource(url);
            InputStream inputStream = Resources.getUrlAsStream(url);
            //映射器比较复杂，调用XMLMapperBuilder
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url == null && mapperClass != null) {
            //10.3使用java类名
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            //直接把这个映射加入配置
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

	//比较id和environment是否相等
  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
