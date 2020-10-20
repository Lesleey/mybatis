package demo.handler;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.apache.ibatis.type.TypeHandler;
import org.apache.log4j.Logger;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Lesleey
 * @date 2019/11/23-16:45
 * @function
 */
@MappedTypes({String.class})
@MappedJdbcTypes(JdbcType.VARCHAR)
public class MyStringHandler implements TypeHandler<String> {
    private Logger log=Logger.getLogger(MyStringHandler.class);

    @Override
    public String getResult(ResultSet rs, String colName) throws SQLException {
        String string = rs.getString(colName);
        System.out.println("用自定义的typeHandler，从" + colName+"列获取结果" + string);
        return rs.getString(colName);
    }

    @Override
    public String getResult(ResultSet rs, int index) throws SQLException {
        System.out.println("用自定义的typeHandler，从" + index+"下标获取结果");
        return rs.getString(index);
    }

    @Override
    public String getResult(CallableStatement cs, int index) throws SQLException {
        System.out.println("用自定义的typeHandler，从" + index+"下标从CallableStatement获取结果");
        return cs.getString(index);
    }

    @Override
    public void setParameter(PreparedStatement ps, int index, String value, JdbcType arg3) throws SQLException {
        System.out.println(("使用我的TypeHandler预编译"));
        ps.setString(index, value);
    }

}