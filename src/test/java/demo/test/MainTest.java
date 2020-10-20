package demo.test;

import demo.Mapper.RoleDao;
import demo.entity.Role;
import ognl.Ognl;
import ognl.OgnlException;
import org.apache.derby.iapi.store.access.conglomerate.Sort;
import org.apache.ibatis.binding.BindingException;
import org.apache.ibatis.builder.ParameterExpression;
import org.apache.ibatis.datasource.pooled.PooledDataSourceFactory;
import org.apache.ibatis.io.DefaultVFS;
import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.SQL;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.scripting.xmltags.OgnlClassResolver;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.type.TypeHandler;
import org.apache.velocity.runtime.parser.node.MapSetExecutor;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.*;

/**
 * @author Lesleey
 * @date 2019/11/24-17:47
 * @function
 */
public class MainTest {
    public static void main(String[] args) throws IOException, SQLException, OgnlException {
        List<String> list = DefaultVFS.getInstance().list("demo/entity");
        list.forEach((k)->{
            System.out.println(k);
        });
        Role role = new Role(1L, "2");
        Map defaultContext = Ognl.createDefaultContext(role, new OgnlClassResolver());
        Object value = Ognl.getValue("id", defaultContext, role);
        System.out.println(value);
    }
}
