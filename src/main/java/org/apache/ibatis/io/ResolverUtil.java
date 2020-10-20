package org.apache.ibatis.io;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 *  工具类，封装了很多方法，有两个静态内部类IsA）和AnnotatedWith
 * */
public class ResolverUtil<T> {

  private static final Log log = LogFactory.getLog(ResolverUtil.class);

  /**
   *  函数式接口，判断该类对象是否满足某一条件
   * */
  public static interface Test {
    boolean matches(Class<?> type);
  }


  /**
   *  判断当前传入的类对象是否为某一类型
   * */
  public static class IsA implements Test {
    private Class<?> parent;

    public IsA(Class<?> parentType) {
      this.parent = parentType;
    }
    @Override
    public boolean matches(Class<?> type) {
      return type != null && parent.isAssignableFrom(type);
    }

    @Override
    public String toString() {
      return "is assignable to " + parent.getSimpleName();
    }
  }

  /**
   *  判断当前传入的类对象是否有某一注解
   * */
  public static class AnnotatedWith implements Test {
    private Class<? extends Annotation> annotation;


    public AnnotatedWith(Class<? extends Annotation> annotation) {
      this.annotation = annotation;
    }

    @Override
    public boolean matches(Class<?> type) {
      return type != null && type.isAnnotationPresent(annotation);
    }

    @Override
    public String toString() {
      return "annotated with @" + annotation.getSimpleName();
    }
  }


  /**
   *  包含所有满足条件的集合
   * */
  private Set<Class<? extends T>> matches = new HashSet<Class<? extends T>>();


  private ClassLoader classloader;


  public Set<Class<? extends T>> getClasses() {
    return matches;
  }


  public ClassLoader getClassLoader() {
    return classloader == null ? Thread.currentThread().getContextClassLoader() : classloader;
  }


  public void setClassLoader(ClassLoader classloader) {
    this.classloader = classloader;
  }

  /**
   *  在包名packagesNames下，寻找所有类对象parent的子类。
   * */
  public ResolverUtil<T> findImplementations(Class<?> parent, String... packageNames) {
    if (packageNames == null) {
      return this;
    }
    Test test = new IsA(parent);
    for (String pkg : packageNames) {
      find(test, pkg);
    }

    return this;
  }

  /**
   * 在包名packageNames下，寻找所有添加annotation注解的类
   * */
  public ResolverUtil<T> findAnnotated(Class<? extends Annotation> annotation, String... packageNames) {
    if (packageNames == null) {
      return this;
    }

    Test test = new AnnotatedWith(annotation);
    for (String pkg : packageNames) {
      find(test, pkg);
    }

    return this;
  }

  /**
   *  找一个package下满足条件的所有类
   * */
  public ResolverUtil<T> find(Test test, String packageName) {
    String path = getPackagePath(packageName);

    try {
        //通过VFS来深入jar包里面去找一个class
      List<String> children = VFS.getInstance().list(path);
      for (String child : children) {
        if (child.endsWith(".class")) {
          addIfMatching(test, child);
        }
      }
    } catch (IOException ioe) {
      log.error("Could not read package: " + packageName, ioe);
    }

    return this;
  }

  /**
   * Converts a Java package name to a path that can be looked up with a call to
   *   将包名转化为目录结构
   * {@link ClassLoader#getResources(String)}.
   * 
   * @param packageName The Java package name to convert to a path
   */
  protected String getPackagePath(String packageName) {
    return packageName == null ? null : packageName.replace('.', '/');
  }

  /**
   * Add the class designated by the fully qualified class name provided to the set of
   * resolved classes if and only if it is approved by the Test supplied.
   *
   * @param test the test used to determine if the class matches
   * @param fqn the fully qualified name of a class 类文件的地址
   *    将给定的类文件地址转化成类对象，然后判断是否满足条件，如果满足添加到 matches 集合中
   */
  @SuppressWarnings("unchecked")
  protected void addIfMatching(Test test, String fqn) {
    try {
      String externalName = fqn.substring(0, fqn.indexOf('.')).replace('/', '.');
      ClassLoader loader = getClassLoader();
      log.debug("Checking to see if class " + externalName + " matches criteria [" + test + "]");

      Class<?> type = loader.loadClass(externalName);
      if (test.matches(type)) {
        matches.add((Class<T>) type);
      }
    } catch (Throwable t) {
      log.warn("Could not examine class '" + fqn + "'" + " due to a " +
          t.getClass().getName() + " with message: " + t.getMessage());
    }
  }
}