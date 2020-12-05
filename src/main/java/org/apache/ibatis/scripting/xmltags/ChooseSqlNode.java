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
package org.apache.ibatis.scripting.xmltags;

import java.util.List;

/**
 * @author Clinton Begin
 */
/**
 * choose SQL节点: 类似于 java中的 switch 语句, 如果内部的<when/>节点，有一个条件满足，则拼装满足条件的那个 <when/> 节点内部的sql代码，
 * 否则，如果存在<otherWise/> 节点， 则拼装 <otherWise/>节点内部的sql代码，
 *
 */
public class ChooseSqlNode implements SqlNode {

  /**
   *  <otherWise/> 节点
   * */
  private SqlNode defaultSqlNode;

  /**
   * <when/> 节点
   * */
  private List<SqlNode> ifSqlNodes;

  public ChooseSqlNode(List<SqlNode> ifSqlNodes, SqlNode defaultSqlNode) {
    this.ifSqlNodes = ifSqlNodes;
    this.defaultSqlNode = defaultSqlNode;
  }

  @Override
  public boolean apply(DynamicContext context) {
    //1. 寻找满足条件的 <when> 节点，如果存在，则拼装，并返回
    for (SqlNode sqlNode : ifSqlNodes) {
      if (sqlNode.apply(context)) {
        return true;
      }
    }
    //2. 如果<when>节点指定的条件都不为true，那就拼装 <otherWise/> 节点内部的sql代码
    if (defaultSqlNode != null) {
      defaultSqlNode.apply(context);
      return true;
    }
    //3. 否则，返回false
    return false;
  }
}
