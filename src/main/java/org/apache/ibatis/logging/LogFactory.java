/*
 *    Copyright 2009-2013 the original author or authors.
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
package org.apache.ibatis.logging;

import org.apache.ibatis.logging.commons.JakartaCommonsLoggingImpl;
import org.apache.ibatis.logging.jdk14.Jdk14LoggingImpl;
import org.apache.ibatis.logging.log4j.Log4jImpl;
import org.apache.ibatis.logging.log4j2.Log4j2Impl;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.logging.slf4j.Slf4jImpl;
import org.apache.ibatis.logging.stdout.StdOutImpl;

import java.lang.reflect.Constructor;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
/**
 * 日志工厂：通过静态工厂模式创建出 mybatis可以使用的日志类
 * 
 */
public final class LogFactory {

  /**
   * Marker to be used by logging implementations that support markers
   *  给支持marker功能的logger使用(目前有slf4j, log4j2)
   */
  public static final String MARKER = "MYBATIS";

  /**
   *  实际集成的日类的构造对象
   * */
  private static Constructor<? extends Log> logConstructor;

  /**
   *  在类加载的过程中，按照以下顺序尝试加载日志类，直到找到一个可以使用的日志类
   * */
  static {
    //slf4j
    tryImplementation(new Runnable() {
      @Override
      public void run() {
        useSlf4jLogging();
      }
    });
    //common logging
    tryImplementation(new Runnable() {
      @Override
      public void run() {
        useCommonsLogging();
      }
    });
    //log4j2
    tryImplementation(new Runnable() {
      @Override
      public void run() {
        useLog4J2Logging();
      }
    });
    //log4j
    tryImplementation(new Runnable() {
      @Override
      public void run() {
        useLog4JLogging();
      }
    });
    //jdk logging
    tryImplementation(new Runnable() {
      @Override
      public void run() {
        useJdkLogging();
      }
    });
    //没有日志
    tryImplementation(new Runnable() {
      @Override
      public void run() {
        useNoLogging();
      }
    });
  }

  //单例模式，不得自己new实例
  private LogFactory() {
  }

  //-------------------------提供了重载方法，获取当前正在使用的日志类实现----------------------------------------------
  public static Log getLog(Class<?> aClass) {
    return getLog(aClass.getName());
  }

  public static Log getLog(String logger) {
    try {
      return logConstructor.newInstance(new Object[] { logger });
    } catch (Throwable t) {
      throw new LogException("Error creating logger for logger " + logger + ".  Cause: " + t, t);
    }
  }

  /**
   *  如果市面上没有可以使用的日志类，则可以实现{@link Log}接口，自定义实现
   * */
  public static synchronized void useCustomLogging(Class<? extends Log> clazz) {
    setImplementation(clazz);
  }

  //-----------------------------设置适配者 日志类---------------------------------
  public static synchronized void useSlf4jLogging() {
    setImplementation(Slf4jImpl.class);
  }

  public static synchronized void useCommonsLogging() {
    setImplementation(JakartaCommonsLoggingImpl.class);
  }

  public static synchronized void useLog4JLogging() {
    setImplementation(Log4jImpl.class);
  }

  public static synchronized void useLog4J2Logging() {
    setImplementation(Log4j2Impl.class);
  }

  public static synchronized void useJdkLogging() {
    setImplementation(Jdk14LoggingImpl.class);
  }
  public static synchronized void useStdOutLogging() {
    setImplementation(StdOutImpl.class);
  }

  public static synchronized void useNoLogging() {
    setImplementation(NoLoggingImpl.class);
  }

  /**
   *  如果当前还没有找到日志类实现，则尝试设置，如果没有对应的jar包，则抛出类找不到异常，然后忽略，尝试下一个
   * */
  private static void tryImplementation(Runnable runnable) {
    if (logConstructor == null) {
      try {
        runnable.run();
      } catch (Throwable t) {
        // ignore
      }
    }
  }

  /**
   * 设置mybatis集成的日志类
   * */
  private static void setImplementation(Class<? extends Log> implClass) {
    try {
      Constructor<? extends Log> candidate = implClass.getConstructor(new Class[] { String.class });
      Log log = candidate.newInstance(new Object[] { LogFactory.class.getName() });
      log.debug("Logging initialized using '" + implClass + "' adapter.");
      logConstructor = candidate;
    } catch (Throwable t) {
      throw new LogException("Error setting Log implementation.  Cause: " + t, t);
    }
  }

}
