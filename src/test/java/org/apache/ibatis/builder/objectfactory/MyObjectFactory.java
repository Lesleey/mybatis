package org.apache.ibatis.builder.objectfactory;

import org.apache.ibatis.builder.typealias.Person;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;

/**
 * @author Lesleey
 * @date 2020/10/20-19:23
 * @function
 */
public class MyObjectFactory extends DefaultObjectFactory {
    @Override
    public <T> T create(Class<T> type) {
        T t  = super.create(type);
        if(t instanceof Person){
            Person person = (Person) t;
            person.setName("凯里欧文");
        }
        return t;
    }
}
