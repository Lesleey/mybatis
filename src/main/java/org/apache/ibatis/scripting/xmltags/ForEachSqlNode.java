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

import java.util.Map;

import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
/**
 * foreach SQL节点

 */
public class ForEachSqlNode implements SqlNode {
  public static final String ITEM_PREFIX = "__frch_";

  /**
   *  表达式求职器，用于解析表达式，获取对应的值
   * */
  private ExpressionEvaluator evaluator;

  /**
   *  集合表达式，用于表达式求值器获取表达式对应的值
   * */
  private String collectionExpression;

  /**
   *  该<forEach/> 内部节点
   * */
  private SqlNode contents;

  /**
   *  开始记号
   * */
  private String open;

  /**
   *  结束记号
   * */
  private String close;

  /**
   *  分隔符
   * */
  private String separator;

  /**
   * 元素名称：用来表示每次迭代获取到的元素， 如果参数为map，则表示为 value
   * */
  private String item;

  /**
   * 索引名称：当前迭代的序号, 如果参数为map，则表示为 key
   * */
  private String index;

  /**
   *  全局配置类
   * */
  private Configuration configuration;

  public ForEachSqlNode(Configuration configuration, SqlNode contents, String collectionExpression, String index, String item, String open, String close, String separator) {
    this.evaluator = new ExpressionEvaluator();
    this.collectionExpression = collectionExpression;
    this.contents = contents;
    this.open = open;
    this.close = close;
    this.separator = separator;
    this.index = index;
    this.item = item;
    this.configuration = configuration;
  }

  /**
   *  处理 <forEach/> 标签的全部具体过程
   *  array = {"a", "b"}
   *   eg: <forEach item = "item", index = "index" open = "(" close = ")" separator="," collectioin="array">
   *          #{item}, #{index}
   *       </forEach>
   *    将会解析成 (#{_frch_item_0_}, #{_frch_index_0_}, #{_frch_item_1_}, #{_frch_index_1_})
   *
   *     在解析之前就会把属性名对应的值存储到动态上下文中，例如属性名"_frch_item_0_" 对应的值为 "a", 在获取具体的sql源时，才会将这些占位符替换为真正的值
   * */
  @Override
  public boolean apply(DynamicContext context) {
    Map<String, Object> bindings = context.getBindings();
    //1. 解析表达式获取集合
    final Iterable<?> iterable = evaluator.evaluateIterable(collectionExpression, bindings);
    //2. 如果为空，则直接返回
    if (!iterable.iterator().hasNext()) {
      return true;
    }
    boolean first = true;
    //3. 拼接开始标记
    applyOpen(context);
    int i = 0;
    //4. 遍历集合（<forEach/> 标签所使用的）中的所有元素
    for (Object o : iterable) {
      DynamicContext oldContext = context;
      //4.1 使用装饰者模式，添加分隔符前缀，集合中的第一个元素不需要添加
      if (first) {
        context = new PrefixedContext(context, "");
      } else if (separator != null) {
        context = new PrefixedContext(context, separator);
      } else {
          context = new PrefixedContext(context, "");
      }
      //4.2 获取唯一标识符
      int uniqueNumber = context.getUniqueNumber();
      // Issue #709
      //4.3 如果为map类型，则index为key, item为value
      if (o instanceof Map.Entry) {
        Map.Entry<Object, Object> mapEntry = (Map.Entry<Object, Object>) o;
        applyIndex(context, mapEntry.getKey(), uniqueNumber);
        applyItem(context, mapEntry.getValue(), uniqueNumber);
      //4.3 如果为其他的集合类型， index为序号， item为正在迭代的元素
      } else {
        applyIndex(context, i, uniqueNumber);
        applyItem(context, o, uniqueNumber);
      }
      //4.4
      contents.apply(new FilteredDynamicContext(configuration, context, index, item, uniqueNumber));
      if (first) {
        first = !((PrefixedContext) context).isPrefixApplied();
      }
      context = oldContext;
      i++;
    }
	//5. 拼装结束记号
    applyClose(context);
    return true;
  }

  /**
   * @param context 当前sql语句节点的动态上下文
   * @param o 如果集合为map，则为key，否则为序号
   * @param i 唯一数字
   *   1.  用于向Ognl中绑定指定的 index 对应的值。
   *   2.  用于根据指定的index生成唯一的属性名，并和值绑定在Ognl中
   * */
  private void applyIndex(DynamicContext context, Object o, int i) {
    if (index != null) {
      context.bind(index, o);
      context.bind(itemizeItem(index, i), o);
    }
  }

  /**
   * @param context 当前sql语句节点的动态上下文
   * @param o 如果集合为map，则为value，否则为集合中的元素
   * @param i 唯一数字
   * */
  private void applyItem(DynamicContext context, Object o, int i) {
    if (item != null) {
      context.bind(item, o);
      context.bind(itemizeItem(item, i), o);
    }
  }

  /**
   *  拼接开始记号
   * */
  private void applyOpen(DynamicContext context) {
    if (open != null) {
      context.appendSql(open);
    }
  }

  private void applyClose(DynamicContext context) {
    if (close != null) {
      context.appendSql(close);
    }
  }

  /**
   * @param item  在<forEach/> 中指定的 item属性的值
   * @param i 唯一数字
   *  用于生成唯一的属性名称，用于拼装 sql语句时保证属性名称唯一
   *   _frch_item_i
   * */
  private static String itemizeItem(String item, int i) {
    return new StringBuilder(ITEM_PREFIX).append(item).append("_").append(i).toString();
  }

  /**
   *  过滤动态上下文，用于将<forEach/> 标签内部的
   * */
  private static class FilteredDynamicContext extends DynamicContext {

    /**
     *  实际的动态上下文
     * */
    private DynamicContext delegate;

    /**
     *  当前的序号
     * */
    private int index;

    /**
     *  <forEach/> 节点 index 属性指定的值
     * */
    private String itemIndex;

    /**
     *  <forEach/> 节点 item 属性指定的值
     * */
    private String item;

    public FilteredDynamicContext(Configuration configuration,DynamicContext delegate, String itemIndex, String item, int i) {
      super(configuration, null);
      this.delegate = delegate;
      this.index = i;
      this.itemIndex = itemIndex;
      this.item = item;
    }

    @Override
    public Map<String, Object> getBindings() {
      return delegate.getBindings();
    }

    @Override
    public void bind(String name, Object value) {
      delegate.bind(name, value);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    @Override
    public void appendSql(String sql) {
      // 用于替换 <forEach/> 标签内部的占位符( item 或者 index )为真实的属性名称，例如 #{item}替换为为唯一的属性名成 #{_frch_item_i_}
      GenericTokenParser parser = new GenericTokenParser("#{", "}", new TokenHandler() {
        @Override
        public String handleToken(String content) {
          String newContent = content.replaceFirst("^\\s*" + item + "(?![^.,:\\s])", itemizeItem(item, index));
          if (itemIndex != null && newContent.equals(content)) {
            newContent = content.replaceFirst("^\\s*" + itemIndex + "(?![^.,:\\s])", itemizeItem(itemIndex, index));
          }
          return new StringBuilder("#{").append(newContent).append("}").toString();
        }
      });

      delegate.appendSql(parser.parse(sql));
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }

  }


  /**
   *  前缀上下文: 使用装饰者模式在拼装 实际的sql前添加前缀
   * */
  private class PrefixedContext extends DynamicContext {

    /**
     *  实际的动态上下文
     * */
    private DynamicContext delegate;

    /**
     *  所要拼接的前缀
     * */
    private String prefix;

    /**
     *  是否已经拼接上前缀
     * */
    private boolean prefixApplied;

    public PrefixedContext(DynamicContext delegate, String prefix) {
      super(configuration, null);
      this.delegate = delegate;
      this.prefix = prefix;
      this.prefixApplied = false;
    }

    public boolean isPrefixApplied() {
      return prefixApplied;
    }

    @Override
    public Map<String, Object> getBindings() {
      return delegate.getBindings();
    }

    @Override
    public void bind(String name, Object value) {
      delegate.bind(name, value);
    }

    @Override
    public void appendSql(String sql) {
      if (!prefixApplied && sql != null && sql.trim().length() > 0) {
        delegate.appendSql(prefix);
        prefixApplied = true;
      }
      delegate.appendSql(sql);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }
  }

}
