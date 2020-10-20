package org.apache.ibatis.builder.typealias;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

/**
 * @author Lesleey
 * @date 2020/10/20-18:54
 * @function
 */
public class CustomTypeAliasProp {
    @Test
    public void testTypeHandler(){
        String resource = "org/apache/ibatis/builder/typealias/AliasConfig.xml";
        try(InputStream inputStream = Resources.getResourceAsStream(resource)){
            XMLConfigBuilder baseBuilder = new XMLConfigBuilder(inputStream);
            Configuration config = baseBuilder.parse();
            baseBuilder.getConfiguration().getTypeAliasRegistry().getTypeAliases().forEach( (k, v)->{
                System.out.println(String.format("别名: %s,  类对象: %s", k, v.getSimpleName()));
                  }
            );
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
