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
package org.apache.ibatis.parsing;

/**
 * @author Clinton Begin
 */
/**
 * 普通记号解析器，处理#{}和${}参数
 * 
 */
public class GenericTokenParser {

  /**
   *  开始记号
   * */
  private final String openToken;

  /**
   *   结束记号
   * */
  private final String closeToken;

  /**
   *   记号处理器
   * */
  private final TokenHandler handler;

  public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
    this.openToken = openToken;
    this.closeToken = closeToken;
    this.handler = handler;
  }

  /**
   *  将开始记号和结束记号之间的内容使用指定的记号处理器进行处理，并将处理后的内容替换之间的内容
   * */
  public String parse(String text) {
    StringBuilder builder = new StringBuilder();
    if (text != null && text.length() > 0) {
      char[] src = text.toCharArray();
      //1. 记录当前已经解析到的位置
      int offset = 0;
      //2. 记录还未解析的位置
      int start = text.indexOf(openToken, offset);
      //3. 循环进行解析，直到 text offset之后没有开始记号
      while (start > -1) {
        //3.1 如果开始记号之前是转义符，直接将 text中 offset到结束标记中间的字符和结束标记添加到 builder 中
        if (start > 0 && src[start - 1] == '\\') {
          //issue #760
          builder.append(src, offset, start - offset - 1).append(openToken);
          offset = start + openToken.length();
        //3.2 开始解析过程
        } else {
          int end = text.indexOf(closeToken, start);
           //3.2.1 如果没有结束符，则直接将 text 中所有的字符添加到 builder 中
          if (end == -1) {
            builder.append(src, offset, src.length - offset);
            offset = src.length;
           //3.2.2 使用记号处理器处理标记之前的内容，并进行替换
          } else {
            builder.append(src, offset, start - offset);
            offset = start + openToken.length();
            String content = new String(src, offset, end - offset);
            builder.append(handler.handleToken(content));
            offset = end + closeToken.length();
          }
        }
        start = text.indexOf(openToken, offset);
      }
      if (offset < src.length) {
        builder.append(src, offset, src.length - offset);
      }
    }
    return builder.toString();
  }

}
