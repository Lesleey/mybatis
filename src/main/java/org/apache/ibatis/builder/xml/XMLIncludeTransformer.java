/*
 * Copyright 2012 MyBatis.org.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.builder.xml;

import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.parsing.PropertyParser;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.parsing.PropertyParser;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.session.Configuration;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Frank D. Martinez [mnesarco]
 */
/**
 * XML include转换器： 解析节点内部所有的 <include/> 节点为真正的 sql 代码
 *
 */
public class XMLIncludeTransformer {

  /**
   *  全局的配置类
   * */
  private final Configuration configuration;

  /**
   *  mapper 构建助手
   * */
  private final MapperBuilderAssistant builderAssistant;

  public XMLIncludeTransformer(Configuration configuration, MapperBuilderAssistant builderAssistant) {
    this.configuration = configuration;
    this.builderAssistant = builderAssistant;
  }

  /**
   *   将 source 节点内部所有的 <include/> 节点替换为真实的 sql 代码
   * */
  public void applyIncludes(Node source) {
    //1. 如果当前是 <include/> 节点
    if (source.getNodeName().equals("include")) {
      //1.1 获取 refid 指定的 <sql/> 节点
      Node toInclude = findSqlFragment(getStringAttribute(source, "refid"));
      //1.2 递归调用自己，因为 <sql/> 节点内部可能会包含 <include/> 节点
      applyIncludes(toInclude);
      //1.3 如果从configuration找到的节点和源节点不属于同一个document时。将它从对应的document拷贝到当前document todo lesleey ？
      if (toInclude.getOwnerDocument() != source.getOwnerDocument()) {
        toInclude = source.getOwnerDocument().importNode(toInclude, true);
      }
      //1.4 将<include/>节点替换为对应的<sql/>节点。
      source.getParentNode().replaceChild(toInclude, source);
      //1.5 将 <sql/> 节点替换为 <sql/> 节点内部的文本内容
      while (toInclude.hasChildNodes()) {
        toInclude.getParentNode().insertBefore(toInclude.getFirstChild(), toInclude);
      }
      toInclude.getParentNode().removeChild(toInclude);

      //2. 如果是其他的元素节点， 获取该节点的所有孩子节点，递归调用本方法
    } else if (source.getNodeType() == Node.ELEMENT_NODE) {
      NodeList children = source.getChildNodes();
      for (int i=0; i<children.getLength(); i++) {
        applyIncludes(children.item(i));
      }
    }
  }

  /**
   *  根据 refid 从 全局配置类中获取对应的 Node 节点
   * */
  private Node findSqlFragment(String refid) {
    //1. 替换所有的占位符
    refid = PropertyParser.parse(refid, configuration.getVariables());
    //2. 根据情况添加 当前的命名空间前缀
    refid = builderAssistant.applyCurrentNamespace(refid, true);
    try {
      //3. 根据获取到的 refid 从configuration 中获取对应的节点，并返回克隆后的节点（防止修改）
      XNode nodeToInclude = configuration.getSqlFragments().get(refid);
      return nodeToInclude.getNode().cloneNode(true);

    } catch (IllegalArgumentException e) {
      throw new IncompleteElementException("Could not find SQL statement to include with refid '" + refid + "'", e);
    }
  }

  /**
   * 获取 node 节点属性为 name 对应的值
   * */
  private String getStringAttribute(Node node, String name) {
    return node.getAttributes().getNamedItem(name).getNodeValue();
  }
}
