package org.apache.ibatis.builder.plugin;

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
public class CustomPluginTest {
    @Test
    public void testProp() throws IOException {
        String resource = "org/apache/ibatis/builder/plugin/PluginConfig.xml";
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            XMLConfigBuilder builder = new XMLConfigBuilder(inputStream);
            Configuration config = builder.parse();
        }
    }
}
