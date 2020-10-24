package org.apache.ibatis.builder.typehandler;

import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Type;

/**
 * @author Lesleey
 * @date 2020/10/22-20:41
 * @function
 */
public class CustomeTypeHandlerTest {
    @Test
    public void testTypeHandler(){
        String resource = "org/apache/ibatis/builder/typehandler/TypeHandlerConfig.xml";
        try(InputStream inputStream = Resources.getResourceAsStream(resource)){
            XMLConfigBuilder baseBuilder = new XMLConfigBuilder(inputStream);
            Configuration config = baseBuilder.parse();
            TypeHandlerRegistry typeHandlerRegistry = baseBuilder.getConfiguration().getTypeHandlerRegistry();
            System.out.println(typeHandlerRegistry.getTypeHandler(String.class).getClass().getSimpleName());
            System.out.println(typeHandlerRegistry.getTypeHandler(Integer.class, JdbcType.INTEGER).getClass().getSimpleName());
        }catch (Exception e){
            e.printStackTrace();
        }

    }
}
