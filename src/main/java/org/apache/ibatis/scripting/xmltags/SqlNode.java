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

/**
 * @author Clinton Begin
 */
/**
 * SQL节点（choose|foreach|if|...）
 *   用于获取动态上下文中保存的参数对象信息，生成该节点所对应的sql语句，并拼装到动态上下文中
 *   该类包含sql语句节点内部的某个孩子节点的全部信息
 */
public interface SqlNode {
  /**
   *  如果当前sql语句节点包含动态sql，则根据参数解析动态sql，将动态sql节点内部的sql代码拼装到 动态上下文保存的sql。
   *  如果包含 ${} 占位符，则根据参数解析成具体的值拼装到 sql中（sql注入的问题）
   * */
  boolean apply(DynamicContext context);
}
