package demo.test;

import demo.entity.Role;
import org.apache.ibatis.domain.misc.CustomBeanWrapper;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.wrapper.ObjectWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;

/**
 * @author Lesleey
 * @date 2019/11/24-12:38
 * @function
 */
public class MyWrapperObjectFactory implements ObjectWrapperFactory {
    @Override
    public boolean hasWrapperFor(Object object) {
        return object.getClass() == Role.class;
    }

    @Override
    public ObjectWrapper getWrapperFor(MetaObject metaObject, Object object) {
        System.out.println(object);
        return new CustomBeanWrapper(metaObject, object);
    }
}
