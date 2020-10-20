package org.apache.ibatis.builder.properties;

import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


/**
 * @author Lesleey
 * @date 2020/10/19-20:38
 * @function
 */
public class CustomPropertiesTest {
    @Test
    public void testProp() throws IOException {
        String resource = "org/apache/ibatis/builder/properties/PropertiesConfig.xml";
        Properties properties = new Properties();
        properties.put("key1","param");
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            XMLConfigBuilder builder = new XMLConfigBuilder(inputStream, null, properties);
            Configuration config = builder.parse();
            config.getVariables().forEach((k, v) ->{
                System.out.println("key: " + k + ", value:" + v);
            });
        }
    }
}
