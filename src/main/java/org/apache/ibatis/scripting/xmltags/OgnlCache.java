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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ognl.Ognl;
import ognl.OgnlException;

import org.apache.ibatis.builder.BuilderException;

/**
 * Caches OGNL parsed expressions.
 *  
 * @see http://code.google.com/p/ibatis/issues/detail?id=342
 * OGNL缓存,大概是因为ognl的性能问题，所以加了一个缓存， 用来缓存Ognl解析出来的Ognl表达式
 *
 * @author Eduardo Macarron
 */
public final class OgnlCache {

  private static final Map<String, Object> expressionCache = new ConcurrentHashMap<String, Object>();

  private OgnlCache() {
    // Prevent Instantiation of Static Class
  }

  /**
   * @param expression ognl表达式
   * @param root 用于获取表达式对应的值的对象
   * */
  public static Object getValue(String expression, Object root) {
    try {
      // 创建一个标准的命名上下文，用于解析Ognl表达式
      Map<Object, OgnlClassResolver> context = Ognl.createDefaultContext(root, new OgnlClassResolver());
      return Ognl.getValue(parseExpression(expression), context, root);
    } catch (OgnlException e) {
      throw new BuilderException("Error evaluating expression '" + expression + "'. Cause: " + e, e);
    }
  }

  private static Object parseExpression(String expression) throws OgnlException {
    Object node = expressionCache.get(expression);
    if (node == null) {
      // 根据 Ognl 语法构建对应的Ognl表达式
      node = Ognl.parseExpression(expression);
      expressionCache.put(expression, node);
    }
    return node;
  }

}
