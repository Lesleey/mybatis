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
package org.apache.ibatis.scripting.xmltags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.scripting.defaults.RawSqlSource;
import org.apache.ibatis.session.Configuration;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Clinton Begin
 */
/**
 * XML脚本构建器
 */
public class XMLScriptBuilder extends BaseBuilder {

  /**
   *  sql 语句节点
   * */
  private XNode context;

  /**
   * 当前节点内部的sql代码是否为动态的
   * */
  private boolean isDynamic;

  /**
   *  参数类型
   * */
  private Class<?> parameterType;

  public XMLScriptBuilder(Configuration configuration, XNode context) {
    this(configuration, context, null);
  }

  public XMLScriptBuilder(Configuration configuration, XNode context, Class<?> parameterType) {
    super(configuration);
    this.context = context;
    this.parameterType = parameterType;
  }

  /**
   *  解析 sql语句节点，获取 SqlSource 对象
   * */
  public SqlSource parseScriptNode() {
    //contents从当前节点中获取所有的sqlNode也就是sql语句。在这里判断是否是动态的，如果
    //1. 解析该 sql语句节点，获取内部所有的 SqlNode
    List<SqlNode> contents = parseDynamicTags(context);
    MixedSqlNode rootSqlNode = new MixedSqlNode(contents);
    SqlSource sqlSource = null;
    //2. 如果为动态（如果内部包含动态sql或者${}就是动态的），则创建DynamicSqlSource 动态sql源
    if (isDynamic) {
      sqlSource = new DynamicSqlSource(configuration, rootSqlNode);
    //3. 如果为静态，则创建 RawSqlSource 原始 sql源
    } else {
      sqlSource = new RawSqlSource(configuration, rootSqlNode, parameterType);
    }
    return sqlSource;
  }

  /**
   *  解析该 sql语句节点，获得所有的 sql节点(SqlNode)
   * */
  List<SqlNode> parseDynamicTags(XNode node) {
    List<SqlNode> contents = new ArrayList<SqlNode>();
    NodeList children = node.getNode().getChildNodes();
    // 遍历该 sql 语句节点的所有孩子节点
    for (int i = 0; i < children.getLength(); i++) {
      XNode child = node.newXNode(children.item(i));
      //1. 如果是文本节点或者是文档中的CDATA部（不会由解析器解析的文本）
      if (child.getNode().getNodeType() == Node.CDATA_SECTION_NODE || child.getNode().getNodeType() == Node.TEXT_NODE) {
        String data = child.getStringBody("");

        TextSqlNode textSqlNode = new TextSqlNode(data);
        //1.1 如果 data 中包含 ${}，则为动态的
        if (textSqlNode.isDynamic()) {
          contents.add(textSqlNode);
          isDynamic = true;
        //1.2 如果是静态的，则直接将 data 封装成 StaticTextSqlNode
        } else {
          contents.add(new StaticTextSqlNode(data));
        }
      //2. 如果是元素节点，则根据不同的节点名称，创建不同的NodeHandler,然后通过该节点处理器，根据该child创建不同的sql节点，添加到contents集合中。
      // issue #628
      } else if (child.getNode().getNodeType() == Node.ELEMENT_NODE) {
        //2.1 根据节点名称获取对应的节点处理器
        String nodeName = child.getNode().getNodeName();
        NodeHandler handler = nodeHandlers(nodeName);
        if (handler == null) {
          throw new BuilderException("Unknown element <" + nodeName + "> in SQL statement.");
        }
        //2.2 使用节点处理器处理当前节点，然后记录当前node为动态的
        handler.handleNode(child, contents);
        isDynamic = true;
      }
    }
    return contents;
  }

  /**
   *  根据节点名称获取对应的节点处理器
   * */
  NodeHandler nodeHandlers(String nodeName) {
    Map<String, NodeHandler> map = new HashMap<String, NodeHandler>();
    map.put("trim", new TrimHandler());
    map.put("where", new WhereHandler());
    map.put("set", new SetHandler());
    map.put("foreach", new ForEachHandler());
    map.put("if", new IfHandler());
    map.put("choose", new ChooseHandler());
    map.put("when", new IfHandler());
    map.put("otherwise", new OtherwiseHandler());
    map.put("bind", new BindHandler());
    return map.get(nodeName);
  }

  //-------------------- https://mybatis.org/mybatis-3/zh/dynamic-sql.html ----------------------------------------


  /**
   *  节点处理器：用于处理给定的节点，并添加到sqlNode集合中
   * */
  private interface NodeHandler {
    /**
     *  @param nodeToHandle sql语句节点内部的动态语句节点
     *  @param targetContents 已经处理完成的所有 SqlNode
     * */
    void handleNode(XNode nodeToHandle, List<SqlNode> targetContents);
  }

  /**
   * bind 元素允许你在 OGNL 表达式以外创建一个变量，并将其绑定到当前的上下文
   *
   * */
  private class BindHandler implements NodeHandler {
    public BindHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      //1. 获取属性名称
      final String name = nodeToHandle.getStringAttribute("name");
      //2. 获取对应的ognl表达式，用于从参数对象中获取实际的值
      final String expression = nodeToHandle.getStringAttribute("value");
      //3. 绑定属性名称和表达式，用于根据名称获取实际的值
      final VarDeclSqlNode node = new VarDeclSqlNode(name, expression);
      targetContents.add(node);
    }
  }

  /**
   *  用于去除指定的开头和结尾, 并添加前后缀
   * */
  private class TrimHandler implements NodeHandler {
    public TrimHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      //1. 解析内部节点
      List<SqlNode> contents = parseDynamicTags(nodeToHandle);
      //2. 获取相关的属性
      MixedSqlNode mixedSqlNode = new MixedSqlNode(contents);
      String prefix = nodeToHandle.getStringAttribute("prefix");
      String prefixOverrides = nodeToHandle.getStringAttribute("prefixOverrides");
      String suffix = nodeToHandle.getStringAttribute("suffix");
      String suffixOverrides = nodeToHandle.getStringAttribute("suffixOverrides");
      //3. 构建 TrimSqlNode 节点
      TrimSqlNode trim = new TrimSqlNode(configuration, mixedSqlNode, prefix, prefixOverrides, suffix, suffixOverrides);
      targetContents.add(trim);
    }
  }

  /**
   *  只有 <where/> 节点内部返回了 sql语句才会插入 "WHERE" 子句，并去除开头的 and、or等等前缀
   * */
  private class WhereHandler implements NodeHandler {
    public WhereHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      List<SqlNode> contents = parseDynamicTags(nodeToHandle);
      MixedSqlNode mixedSqlNode = new MixedSqlNode(contents);
      WhereSqlNode where = new WhereSqlNode(configuration, mixedSqlNode);
      targetContents.add(where);
    }
  }

  /**
   *  只有<set/>节点内部返回了 sql语句才会插入 "set"子句，并去除结尾的 ","
   * */
  private class SetHandler implements NodeHandler {
    public SetHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      List<SqlNode> contents = parseDynamicTags(nodeToHandle);
      MixedSqlNode mixedSqlNode = new MixedSqlNode(contents);
      SetSqlNode set = new SetSqlNode(configuration, mixedSqlNode);
      targetContents.add(set);
    }
  }

  /**
   *  用于处理对集合的遍历
   * */
  private class ForEachHandler implements NodeHandler {
    public ForEachHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      //1. 解析<forEach/> 节点内部的所有节点
      List<SqlNode> contents = parseDynamicTags(nodeToHandle);
      MixedSqlNode mixedSqlNode = new MixedSqlNode(contents);
      //2. 获取该节点的所有属性
      String collection = nodeToHandle.getStringAttribute("collection");
      String item = nodeToHandle.getStringAttribute("item");
      String index = nodeToHandle.getStringAttribute("index");
      String open = nodeToHandle.getStringAttribute("open");
      String close = nodeToHandle.getStringAttribute("close");
      String separator = nodeToHandle.getStringAttribute("separator");
      //3. 构建 ForEachSqlNode，并添加到集合中
      ForEachSqlNode forEachSqlNode = new ForEachSqlNode(configuration, mixedSqlNode, collection, index, item, open, close, separator);
      targetContents.add(forEachSqlNode);
    }
  }

  /**
   * <if/>标签提供了可选 的sql语句功能，只有表达式为真时，才会拼装内部的sql语句
   * */
  private class IfHandler implements NodeHandler {
    public IfHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      //1. 解析所有的内部节点
      List<SqlNode> contents = parseDynamicTags(nodeToHandle);
      MixedSqlNode mixedSqlNode = new MixedSqlNode(contents);
      //2. 获取test 属性的值
      String test = nodeToHandle.getStringAttribute("test");
      //3. 构建 IfSqlNode 节点
      IfSqlNode ifSqlNode = new IfSqlNode(mixedSqlNode, test);
      targetContents.add(ifSqlNode);
    }
  }

  /**
   *  如果<choose/>节点中的所有的条件都不满足，可以选择一个默认的使用，类似与 java中的 default
   * */
  private class OtherwiseHandler implements NodeHandler {
    public OtherwiseHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      List<SqlNode> contents = parseDynamicTags(nodeToHandle);
      MixedSqlNode mixedSqlNode = new MixedSqlNode(contents);
      targetContents.add(mixedSqlNode);
    }
  }

  /**
   *  如果不想使用所有的条件，而是在多个条件中选择一个使用，可以使用 <choose/> 节点，类似于java中的 Switch
   * */
  private class ChooseHandler implements NodeHandler {
    public ChooseHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      //1. 使用两个集合，分别用来存储 <choose/> 节点中所有的 <when/> 节点和 <otherwise />节点
      List<SqlNode> whenSqlNodes = new ArrayList<SqlNode>();
      List<SqlNode> otherwiseSqlNodes = new ArrayList<SqlNode>();
      //2. 解析内部所有的节点，并将对应的节点添加到集合中
      handleWhenOtherwiseNodes(nodeToHandle, whenSqlNodes, otherwiseSqlNodes);
      //3. 从 otherwiseSqlNode中获取默认的 sqlNode
      SqlNode defaultSqlNode = getDefaultSqlNode(otherwiseSqlNodes);
      //4. 构建 chooseSqlNode
      ChooseSqlNode chooseSqlNode = new ChooseSqlNode(whenSqlNodes, defaultSqlNode);
      targetContents.add(chooseSqlNode);
    }

    private void handleWhenOtherwiseNodes(XNode chooseSqlNode, List<SqlNode> ifSqlNodes, List<SqlNode> defaultSqlNodes) {
      //1. 获取该 <choose/> 节点内部所有的孩子节点
      List<XNode> children = chooseSqlNode.getChildren();
      //2. 遍历所有的孩子节点
      for (XNode child : children) {
        //2.1 获取节点名称(when, otherwise)
        String nodeName = child.getNode().getNodeName();
        //2.2 根据节点名称获取节点处理器
        NodeHandler handler = nodeHandlers(nodeName);
        if (handler instanceof IfHandler) {
          handler.handleNode(child, ifSqlNodes);
        } else if (handler instanceof OtherwiseHandler) {
          handler.handleNode(child, defaultSqlNodes);
        }
      }
    }

    /**
     *  保证 <otherWise/> 节点只有一个
     * */
    private SqlNode getDefaultSqlNode(List<SqlNode> defaultSqlNodes) {
      SqlNode defaultSqlNode = null;
      if (defaultSqlNodes.size() == 1) {
        defaultSqlNode = defaultSqlNodes.get(0);
      } else if (defaultSqlNodes.size() > 1) {
        throw new BuilderException("Too many default (otherwise) elements in choose statement.");
      }
      return defaultSqlNode;
    }
  }

}
