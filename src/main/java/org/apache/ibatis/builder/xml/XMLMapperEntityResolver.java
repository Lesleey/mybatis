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
package org.apache.ibatis.builder.xml;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.ibatis.io.Resources;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Offline entity resolver for the MyBatis DTDs
 * 目的是未联网的情况下也能做DTD验证，实现原理就是将DTD搞到本地，然后用org.xml.sax.EntityResolver，最后调用DocumentBuilder.setEntityResolver来达到脱机验证
 * EntityResolver
 * public InputSource resolveEntity (String publicId, String systemId)
 * 应用程序可以使用此接口将系统标识符重定向到本地 URI
 * 但是用DTD是比较过时的做法，新的都改用xsd了
 * 这个类的名字并不准确，因为它被两个类都用到了（XMLConfigBuilder,XMLMapperBuilder）
 *   主要用来验证 ibatis 配置文件xml格式的准确性。 通过向本地保存一份DTD文件，使在未联网的情况下也能对格式进行验证。
 *   注意： 使用DTD验证方式比较过时，较新的方式使用 xsd 方式
 * 
 * @author Clinton Begin
 */
public class XMLMapperEntityResolver implements EntityResolver {

  private static final Map<String, String> doctypeMap = new HashMap<String, String>();

	// <?xml version="1.0" encoding="UTF-8" ?>
	// <!DOCTYPE mapper PUBLIC "-//ibatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
  private static final String IBATIS_CONFIG_PUBLIC = "-//ibatis.apache.org//DTD Config 3.0//EN".toUpperCase(Locale.ENGLISH);
  private static final String IBATIS_CONFIG_SYSTEM = "http://ibatis.apache.org/dtd/ibatis-3-config.dtd".toUpperCase(Locale.ENGLISH);

  private static final String IBATIS_MAPPER_PUBLIC = "-//ibatis.apache.org//DTD Mapper 3.0//EN".toUpperCase(Locale.ENGLISH);
  private static final String IBATIS_MAPPER_SYSTEM = "http://ibatis.apache.org/dtd/ibatis-3-mapper.dtd".toUpperCase(Locale.ENGLISH);

  private static final String MYBATIS_CONFIG_PUBLIC = "-//ibatis.org//DTD Config 3.0//EN".toUpperCase(Locale.ENGLISH);
  private static final String MYBATIS_CONFIG_SYSTEM = "http://ibatis.org/dtd/ibatis-3-config.dtd".toUpperCase(Locale.ENGLISH);

  private static final String MYBATIS_MAPPER_PUBLIC = "-//ibatis.org//DTD Mapper 3.0//EN".toUpperCase(Locale.ENGLISH);
  private static final String MYBATIS_MAPPER_SYSTEM = "http://ibatis.org/dtd/ibatis-3-mapper.dtd".toUpperCase(Locale.ENGLISH);

  private static final String MYBATIS_CONFIG_DTD = "org/apache/ibatis/builder/xml/ibatis-3-config.dtd";
  private static final String MYBATIS_MAPPER_DTD = "org/apache/ibatis/builder/xml/ibatis-3-mapper.dtd";

  static {

	/**
     * 将DOCTYPE和URL都映射到本地类路径下的DTD
     * */
    doctypeMap.put(IBATIS_CONFIG_SYSTEM, MYBATIS_CONFIG_DTD);
    doctypeMap.put(IBATIS_CONFIG_PUBLIC, MYBATIS_CONFIG_DTD);

    doctypeMap.put(IBATIS_MAPPER_SYSTEM, MYBATIS_MAPPER_DTD);
    doctypeMap.put(IBATIS_MAPPER_PUBLIC, MYBATIS_MAPPER_DTD);

    doctypeMap.put(MYBATIS_CONFIG_SYSTEM, MYBATIS_CONFIG_DTD);
    doctypeMap.put(MYBATIS_CONFIG_PUBLIC, MYBATIS_CONFIG_DTD);

    doctypeMap.put(MYBATIS_MAPPER_SYSTEM, MYBATIS_MAPPER_DTD);
    doctypeMap.put(MYBATIS_MAPPER_PUBLIC, MYBATIS_MAPPER_DTD);
  }

  /*
   * Converts a public DTD into a local one
   * 
   * @param publicId The public id that is what comes after "PUBLIC"
   * @param systemId The system id that is what comes after the public id.
   * @return The InputSource for the DTD
   * 
   * @throws org.xml.sax.SAXException If anything goes wrong
   */


  /**
   * 通过覆盖 {@link EntityResolver#resolveEntity(String, String)}方法, 使得从本地获取 dtd 文件的流， 然后进行脱机验证
   * */
  @Override
  public InputSource resolveEntity(String publicId, String systemId) throws SAXException {

    if (publicId != null) {
      publicId = publicId.toUpperCase(Locale.ENGLISH);
    }
    if (systemId != null) {
      systemId = systemId.toUpperCase(Locale.ENGLISH);
    }

    InputSource source = null;
    try {
      //1. 找到 publicId 对应的 dtd 文件的路径
      String path = doctypeMap.get(publicId);
      source = getInputSource(path, source);
      //2. 如果找不到，则寻找 systemId 对应的 dtd的文件路径
      if (source == null) {
        path = doctypeMap.get(systemId);
        source = getInputSource(path, source);
      }
    } catch (Exception e) {
      throw new SAXException(e.toString());
    }
    return source;
  }

  /**
   *  使用 ibatis 的工具类 {@link Resources} 读取相对路径下的文件
   * */
  private InputSource getInputSource(String path, InputSource source) {
    if (path != null) {
      InputStream in;
      try {
        in = Resources.getResourceAsStream(path);
        source = new InputSource(in);
      } catch (IOException e) {
      }
    }
    return source;
  }

}