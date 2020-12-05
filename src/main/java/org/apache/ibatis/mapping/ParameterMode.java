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
package org.apache.ibatis.mapping;

/**
 * @author Clinton Begin
 */
/**
 * 
 * 参数模式（sql  函数 | 存储过程）
 * IN: 表示参数往函数里传值
 * OUT: 参数表示函数的输出值（返回值）
 * INOUT： 参数即用来传值，又用来接收返回值
 */
public enum ParameterMode {
  IN, OUT, INOUT
}
