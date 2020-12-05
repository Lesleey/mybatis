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
package org.apache.ibatis.executor.keygen;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 */
/**
 * JDBC3键值生成器,核心是使用JDBC3的Statement.getGeneratedKeys：相当于将自动递增的键作为 keyColumn
 * 
 */
public class Jdbc3KeyGenerator implements KeyGenerator {

  @Override
  public void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    // do nothing
  }

  @Override
  public void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    List<Object> parameters = new ArrayList<Object>();
    parameters.add(parameter);
    processBatch(ms, stmt, parameters);
  }

  /**
   *  对多个参数进行批处理工作（填充键值）
   * */
  public void processBatch(MappedStatement ms, Statement stmt, List<Object> parameters) {
    ResultSet rs = null;
    try {
      //1. 获取自动递增的列的值作为结果集，如果不存在，则该结果集为空
      rs = stmt.getGeneratedKeys();
      final Configuration configuration = ms.getConfiguration();
      //2. 获取类型处理注册器，用于获取合适的类型处理器
      final TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
      //3. 获取指定的 keyProperties 键属性
      final String[] keyProperties = ms.getKeyProperties();
      final ResultSetMetaData rsmd = rs.getMetaData();
      TypeHandler<?>[] typeHandlers = null;
      //4. 如果结果集中返回的列数小于指定的键属性的数量，则直接返回
      if (keyProperties != null && rsmd.getColumnCount() >= keyProperties.length) {
        //5. 遍历所有的参数，进行填充键值的工作
        for (Object parameter : parameters) {
          // there should be one row for each statement (also one for each parameter)
          //5.1 如果当前游标已经指向了最后一行，则退出循环
          if (!rs.next()) {
            break;
          }
          //5.2 构建元数据，用于设置某个属性的值
          final MetaObject metaParam = configuration.newMetaObject(parameter);

          if (typeHandlers == null) {
            typeHandlers = getTypeHandlers(typeHandlerRegistry, metaParam, keyProperties);
          }
          //5.3 填充键值
          populateKeys(rs, metaParam, keyProperties, typeHandlers);
        }
      }
    } catch (Exception e) {
      throw new ExecutorException("Error getting generated key or setting result to parameter object. Cause: " + e, e);
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch (Exception e) {
          // ignore
        }
      }
    }
  }

  /**
   *  根据元数据中所有属性的类型，获取对应的类型处理器
   * */
  private TypeHandler<?>[] getTypeHandlers(TypeHandlerRegistry typeHandlerRegistry, MetaObject metaParam, String[] keyProperties) {
    TypeHandler<?>[] typeHandlers = new TypeHandler<?>[keyProperties.length];
    // 遍历所有指定的 keyProperties，获取对应的类型以及类型处理器
    for (int i = 0; i < keyProperties.length; i++) {
      // 如果该属性有set调用者
      if (metaParam.hasSetter(keyProperties[i])) {
        // 获取该 keyProperties 的类型，并获取对应的类型处理器
        Class<?> keyPropertyType = metaParam.getSetterType(keyProperties[i]);
        TypeHandler<?> th = typeHandlerRegistry.getTypeHandler(keyPropertyType);
        typeHandlers[i] = th;
      }
    }
    return typeHandlers;
  }

  /**
   * 根据结果集，为参数对象填充 keyProperties 指定的所有属性的值
   * */
  private void populateKeys(ResultSet rs, MetaObject metaParam, String[] keyProperties, TypeHandler<?>[] typeHandlers) throws SQLException {
    for (int i = 0; i < keyProperties.length; i++) {
      TypeHandler<?> th = typeHandlers[i];
      if (th != null) {
        Object value = th.getResult(rs, i + 1);
        metaParam.setValue(keyProperties[i], value);
      }
    }
  }

}
