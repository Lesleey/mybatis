package org.apache.ibatis.builder.plugin;

import java.util.Properties;

import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;

@Intercepts({})
public class ExamplePlugin implements Interceptor {
  private Properties properties;

  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    return invocation.proceed();
  }

  @Override
  public Object plugin(Object target) {
    return Plugin.wrap(target, this);
  }

  @Override
  public void setProperties(Properties properties) {
    this.properties = properties;
    System.out.println("设置插件自定义属性");
    properties.forEach((k, v) ->{
      System.out.println(String.format("属性名=%s, 属性值=%s", k, v));
    });
  }

  public Properties getProperties() {
    return properties;
  }

}
