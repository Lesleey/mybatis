package demo.test;

import demo.entity.Role;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.hamcrest.core.IsCollectionContaining;

import java.util.Collection;
import java.util.List;
import java.util.Properties;

/**
 * @author Lesleey
 * @date 2019/11/25-8:44
 * @function
 */
public class MyObjectFactory extends DefaultObjectFactory {

    //处理默认构造方法
    public Object create(Class type){

        if(type == Role.class){
            System.out.println("调用了create(type)");
            Role role = (Role) super.create(type);
            role.setId(3);
            role.setRoleName("lisi");
            return role;
        }
        return super.create(type);
    }
    @Override
    public <T> T create(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
        T t = super.create(type, constructorArgTypes, constructorArgs);
        if(type == Role.class){
            System.out.println("调用了create(....)");
            Role role = (Role) t;
            if(constructorArgTypes != null && constructorArgs!= null) {
                constructorArgTypes.forEach(System.out::print);
                System.out.println();
                constructorArgs.forEach(System.out::print);
            }
            role.setId(1);
            role.setRoleName("wangwu");
        }
        return t;
    }

    //处理参数
    public void setProperties(Properties properties){
        System.out.println("调用了设置属性setProperties");
        super.setProperties(properties);
    }



}
