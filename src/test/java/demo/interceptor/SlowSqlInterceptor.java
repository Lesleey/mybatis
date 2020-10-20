package demo.interceptor;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;

import java.sql.Statement;
import java.util.Properties;

/**
 * @author Lesleey
 * @date 2019/12/9-21:16
 * @function
 */
@Intercepts(
        @Signature(type=StatementHandler.class, method = "query" ,args={Statement.class,ResultHandler.class} )
)
public class SlowSqlInterceptor implements Interceptor {
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        System.out.println("调用了我的慢查询插件");
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {

    }
}
