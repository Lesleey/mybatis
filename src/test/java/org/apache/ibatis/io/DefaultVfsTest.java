package org.apache.ibatis.io;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.ibatis.io.ClassLoaderWrapper;
import org.apache.ibatis.io.DefaultVFS;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
/**
 * @author Lesleey
 * @date 2020/10/12-19:56
 * @function
 */
public class DefaultVfsTest {
    private DefaultVFS defaultVFS = new DefaultVFS() ;
    private ClassLoaderWrapper classLoaderWrapper = new ClassLoaderWrapper();

    @Test
    public void getJarUrl() throws MalformedURLException {
        String jarPath = "org/apache/ibatis/io/mongo-2.10.2.jar";
        URL jarForResource = defaultVFS.findJarForResource(classLoaderWrapper.getResourceAsURL(jarPath));
        Assertions.assertNotNull(jarForResource);
    }
}
