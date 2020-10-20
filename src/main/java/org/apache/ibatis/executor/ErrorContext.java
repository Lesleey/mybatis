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
package org.apache.ibatis.executor;


/**
 * 
 *  错误上下文， 通过 ThreadLocal 的方式，跟踪并打印每个线程的执行情况。
 */
public class ErrorContext {

  /**
   *  获取换行符，不同的OS换行符可能会不同
   * */
  private static final String LINE_SEPARATOR = System.getProperty("line.separator","\n");

  /**
   *  线程副本，跟踪每个线程执行情况
   * */
  private static final ThreadLocal<ErrorContext> LOCAL = new ThreadLocal<ErrorContext>();

  private ErrorContext stored;

  /**
   *  正在处理的mapper 资源路径
   * */
  private String resource;

  /**
   *  活动描述，例如 执行查询、执行更新、设置参数等等
   * */
  private String activity;

  /**
   *  出现问题的对象的唯一标识（例如ID: mybatis的ID都是有意义的，如ResultMap的ID就为该节点的路径）
   * */
  private String object;

  /**
   *  异常原因
   * */
  private String message;

  /**
   *  正在操作的sql语句
   * */
  private String sql;

  /**
   *  出现的异常对象
   * */
  private Throwable cause;
 
  private ErrorContext() {
  }

  /**
   *  单例模式（懒汉式）
   * */
  public static ErrorContext instance() {

    ErrorContext context = LOCAL.get();
    if (context == null) {
       context = new ErrorContext();
       LOCAL.set(context);
    }
    return context;
  }

  //------------------类似于备忘录模式，使得键值生成器的处理逻辑和当前执行隔离----------------------------
  public ErrorContext store() {
    stored = this;
    LOCAL.set(new ErrorContext());
    return LOCAL.get();
  }

  public ErrorContext recall() {
    if (stored != null) {
      LOCAL.set(stored);
      stored = null;
    }
    return LOCAL.get();
  }

  //---------------------通过建造者模式构建对象--------------------------------------
  public ErrorContext resource(String resource) {
    this.resource = resource;
    return this;
  }

  public ErrorContext activity(String activity) {
    this.activity = activity;
    return this;
  }

  public ErrorContext object(String object) {
    this.object = object;
    return this;
  }

  public ErrorContext message(String message) {
    this.message = message;
    return this;
  }

  public ErrorContext sql(String sql) {
    this.sql = sql;
    return this;
  }

  public ErrorContext cause(Throwable cause) {
    this.cause = cause;
    return this;
  }

  /**
   *  清空该对象
   * */
  public ErrorContext reset() {
    resource = null;
    activity = null;
    object = null;
    message = null;
    sql = null;
    cause = null;
    LOCAL.remove();
    return this;
  }

  /**
   *  格式化 toString， 方便使用者定位问题
   * */
  @Override
  public String toString() {
    StringBuilder description = new StringBuilder();

    // message
    if (this.message != null) {
      description.append(LINE_SEPARATOR);
      description.append("### ");
      description.append(this.message);
    }

    // resource
    if (resource != null) {
      description.append(LINE_SEPARATOR);
      description.append("### The error may exist in ");
      description.append(resource);
    }

    // object
    if (object != null) {
      description.append(LINE_SEPARATOR);
      description.append("### The error may involve ");
      description.append(object);
    }

    // activity
    if (activity != null) {
      description.append(LINE_SEPARATOR);
      description.append("### The error occurred while ");
      description.append(activity);
    }

    // activity
    if (sql != null) {
      description.append(LINE_SEPARATOR);
      description.append("### SQL: ");
      //把sql压缩到一行里
      description.append(sql.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim());
    }

    // cause
    if (cause != null) {
      description.append(LINE_SEPARATOR);
      description.append("### Cause: ");
      description.append(cause.toString());
    }

    return description.toString();
  }

}
