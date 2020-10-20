package org.apache.ibatis.builder.objectfactory;

import org.apache.ibatis.builder.typealias.Person;
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
public class CustomObjectFactoryTest {
    @Test
    public void testProp() throws IOException {
        String resource = "org/apache/ibatis/builder/objectfactory/ObjectFactoryConfig.xml";
        Properties properties = new Properties();
        properties.put("classname","MyObjectFactory");
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            XMLConfigBuilder builder = new XMLConfigBuilder(inputStream, null, properties);
            Configuration config = builder.parse();
            Person person = config.getObjectFactory().create(Person.class);
            System.out.println(person.getName());
        }
    }
}
