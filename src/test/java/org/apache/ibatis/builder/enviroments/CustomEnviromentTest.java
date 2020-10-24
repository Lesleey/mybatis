package org.apache.ibatis.builder.enviroments;

import org.apache.ibatis.builder.typealias.Person;
import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Properties;


/**
 * @author Lesleey
 * @date 2020/10/19-20:38
 * @function
 */
public class CustomEnviromentTest {
    @Test
    public void testProp() throws IOException {
        String resource = "org/apache/ibatis/builder/enviroments/EnviromentConfig.xml";
        Properties properties = new Properties();
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            XMLConfigBuilder builder = new XMLConfigBuilder(inputStream, null, properties);
            Configuration config = builder.parse();
            Environment environment = config.getEnvironment();
            System.out.println(environment.getId());
            System.out.println(environment.getDataSource().getConnection().getMetaData().getDatabaseProductName());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
