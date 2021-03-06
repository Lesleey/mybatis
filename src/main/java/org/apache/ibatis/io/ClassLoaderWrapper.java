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
package org.apache.ibatis.io;

import java.io.InputStream;
import java.net.URL;

/**
 * A class to wrap access to multiple class loaders making them work as one
 *  封装了5个类加载器,见{@link ClassLoaderWrapper#getClassLoaders(ClassLoader)}
 *  可以通过该类可以根据资源名称（绝对地址、相对于类路径下的相对地址）获取资源的URL、输入流和类对象等等
 * @author Clinton Begin
 */
public class ClassLoaderWrapper {

  /**
   *  默认的类加载器
   * */
  ClassLoader defaultClassLoader;

  /**
   *  系统类加载器
   * */
  ClassLoader systemClassLoader;


  /**
   *  类初始化时，初始化系统类加载器
   * */
  ClassLoaderWrapper() {
    try {
      systemClassLoader = ClassLoader.getSystemClassLoader();
    } catch (SecurityException ignored) {
    }
  }
  
  /*
   * Get a resource as a URL using the current class path
   *
   *  根据资源名称/路径，有内部选择合适的类加载器获取资源对应的URL
   *
   * @param resource - the resource to locate
   * @return the resource or null
   */
  public URL getResourceAsURL(String resource) {
    return getResourceAsURL(resource, getClassLoaders(null));
  }

  /*
   * Get a resource from the classpath, starting with a specific class loader
   *
   *  根据指定的类加载器获取资源对应的URL
   *
   * @param resource    - the resource to find
   * @param classLoader - the first classloader to try
   * @return the stream or null
   */
  public URL getResourceAsURL(String resource, ClassLoader classLoader) {
    return getResourceAsURL(resource, getClassLoaders(classLoader));
  }

  /*
   * Get a resource from the classpath
   *
   *   由内部选择合适的的类加载器， 根据资源名称获取对应的流
   *
   * @param resource - the resource to find
   * @return the stream or null
   *
   */
  public InputStream getResourceAsStream(String resource) {
    return getResourceAsStream(resource, getClassLoaders(null));
  }

  /*
   * Get a resource from the classpath, starting with a specific class loader
   *
   *   使用指定的类加载器， 根据资源名称获取对应的流
   *
   * @param resource    - the resource to find
   * @param classLoader - the first class loader to try
   * @return the stream or null
   */
  public InputStream getResourceAsStream(String resource, ClassLoader classLoader) {
    return getResourceAsStream(resource, getClassLoaders(classLoader));
  }

  /*
   * Find a class on the classpath (or die trying)
   *
   *    由内部选择合适的的类加载器， 根据类全限定名获取对应的类对象
   *
   * @param name - the class to look for
   * @return - the class
   * @throws ClassNotFoundException Duh.
   */
  public Class<?> classForName(String name) throws ClassNotFoundException {
    return classForName(name, getClassLoaders(null));
  }

  /*
   * Find a class on the classpath, starting with a specific classloader (or die trying)
   *
   *  根据指定的类加载器， 根据类全限定名获取对应的类对象
   *
   * @param name        - the class to look for
   * @param classLoader - the first classloader to try
   * @return - the class
   * @throws ClassNotFoundException Duh.
   */
  public Class<?> classForName(String name, ClassLoader classLoader) throws ClassNotFoundException {
    return classForName(name, getClassLoaders(classLoader));
  }

  /*
   * Try to get a resource from a group of classloaders
   *
   *  遍历内部的 5 个类加载器加载资源获取流，只要其中任何一个找到，就返回
   *
   * @param resource    - the resource to get
   * @param classLoader - the classloaders to examine
   * @return the resource or null
   */
  InputStream getResourceAsStream(String resource, ClassLoader[] classLoader) {
    for (ClassLoader cl : classLoader) {
      if (null != cl) {

        // try to find the resource as passed
        InputStream returnValue = cl.getResourceAsStream(resource);

        // now, some class loaders want this leading "/", so we'll add it and try again if we didn't find the resource
        if (null == returnValue) {
          returnValue = cl.getResourceAsStream("/" + resource);
        }

        if (null != returnValue) {
          return returnValue;
        }
      }
    }
    return null;
  }

  /*
   * Get a resource as a URL using the current class path
   *  使用 数组classLoader中提供的类加载器 加载资源，只要其中任何一个找到，就返回
   *
   * @param resource    - the resource to locate
   * @param classLoader - the class loaders to examine
   * @return the resource or null
   */
  URL getResourceAsURL(String resource, ClassLoader[] classLoader) {

    URL url;

    for (ClassLoader cl : classLoader) {

      if (null != cl) {

        // look for the resource as passed in...
        url = cl.getResource(resource);

        // ...but some class loaders want this leading "/", so we'll add it
        // and try again if we didn't find the resource
        if (null == url) {
          url = cl.getResource("/" + resource);
        }

        // "It's always in the last place I look for it!"
        // ... because only an idiot would keep looking for it after finding it, so stop looking already.
        if (null != url) {
          return url;
        }

      }

    }

    // didn't find it anywhere.
    return null;

  }

  /*
   * Attempt to load a class from a group of classloaders
   *
   *   使用 数组classLoader中提供的类加载器 进行类加载 Class.forName(加载类)，只要其中任何一个加载成功，就返回
   *
   * @param name        - the class to load
   * @param classLoader - the group of classloaders to examine
   * @return the class
   * @throws ClassNotFoundException - Remember the wisdom of Judge Smails: Well, the world needs ditch diggers, too.
   */
  Class<?> classForName(String name, ClassLoader[] classLoader) throws ClassNotFoundException {

    for (ClassLoader cl : classLoader) {

      if (null != cl) {

        try {

          Class<?> c = Class.forName(name, true, cl);

          if (null != c) {
            return c;
          }

        } catch (ClassNotFoundException e) {
          // we'll ignore this until all classloaders fail to locate the class
        }

      }

    }

    throw new ClassNotFoundException("Cannot find class: " + name);

  }


 /**
  *  ibatis 内部提供的 5 个默认的类加载器
  * */
  ClassLoader[] getClassLoaders(ClassLoader classLoader) {
    return new ClassLoader[]{
        classLoader,
        defaultClassLoader,
        Thread.currentThread().getContextClassLoader(),
        getClass().getClassLoader(),
        systemClassLoader};
  }

}
